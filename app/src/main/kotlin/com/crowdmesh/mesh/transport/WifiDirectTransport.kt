package com.crowdmesh.mesh.transport

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import androidx.core.content.ContextCompat
import com.crowdmesh.di.ApplicationScope
import com.crowdmesh.domain.model.TransportKind
import com.crowdmesh.mesh.discovery.DiscoveredPeer
import com.crowdmesh.mesh.protocol.ProtocolConstants
import com.crowdmesh.util.Logger
import com.crowdmesh.util.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Secondary transport using classic Wi-Fi Direct (P2P). "Thinner" than
 * [com.crowdmesh.mesh.transport.BleTransport] in two deliberate ways:
 *  - Discovery uses plain [WifiP2pManager.discoverPeers] (sees *any* nearby
 *    P2P device), not the heavier Bonjour-style local-service/DNS-SD APIs
 *    that would let us filter to CrowdMesh peers before connecting.
 *  - Once a group forms, data moves over a plain TCP socket with simple
 *    length-prefixed framing — no BLE-style MTU chunking is needed since a
 *    socket has no per-write size ceiling.
 */
@Singleton
class WifiDirectTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : Transport {

    override val kind: TransportKind = TransportKind.WIFI_DIRECT

    private val wifiP2pManager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }
    private var channel: WifiP2pManager.Channel? = null
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    private val _incomingConnections = MutableSharedFlow<TransportConnection>(extraBufferCapacity = 4)
    override val incomingConnections: SharedFlow<TransportConnection> = _incomingConnections.asSharedFlow()

    override fun isAvailable(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT) && wifiP2pManager != null

    @SuppressLint("MissingPermission")
    override fun startAdvertising(identityPayload: ByteArray) {
        // Plain Wi-Fi Direct has no pre-connection payload channel without the
        // DNS-SD service-info APIs (out of scope here) — being connectable and
        // ready to accept a group-owner socket is what "advertising" means.
        ensureChannel()
        startAcceptLoopIfNeeded()
    }

    override fun stopAdvertising() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    @SuppressLint("MissingPermission")
    override fun discoverPeers(dutyCycle: MeshDutyCycle): Flow<DiscoveredPeer> = callbackFlow {
        val manager = wifiP2pManager
        val ch = ensureChannel()
        if (manager == null || ch == null || !isAvailable()) {
            close()
            return@callbackFlow
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.action != WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION) return
                manager.requestPeers(ch) { peerList: WifiP2pDeviceList ->
                    peerList.deviceList.forEach { device ->
                        trySend(
                            DiscoveredPeer(
                                connectionHandle = device.deviceAddress,
                                transportKind = TransportKind.WIFI_DIRECT,
                                rssi = null,
                                timestampMillis = timeProvider.nowMillis(),
                            )
                        )
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        manager.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = Logger.d(TAG, "P2P peer discovery started")
            override fun onFailure(reasonCode: Int) = Logger.w(TAG, "P2P peer discovery failed: $reasonCode")
        })

        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }

    @SuppressLint("MissingPermission")
    override suspend fun openConnection(peer: DiscoveredPeer): TransportConnection? {
        val manager = wifiP2pManager ?: return null
        val ch = ensureChannel() ?: return null

        val config = WifiP2pConfig().apply { deviceAddress = peer.connectionHandle }
        val requestAccepted = suspendCancellableCoroutine { cont ->
            manager.connect(ch, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    if (cont.isActive) cont.resume(true)
                }
                override fun onFailure(reasonCode: Int) {
                    Logger.w(TAG, "P2P connect request failed: $reasonCode")
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
        if (!requestAccepted) return null

        val info = awaitGroupFormed(manager, ch) ?: return null
        if (info.isGroupOwner) {
            // We ended up as group owner; the peer will dial into our accept
            // loop (see startAcceptLoopIfNeeded) and surface via incomingConnections.
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(
                    InetSocketAddress(info.groupOwnerAddress.hostAddress, SOCKET_PORT),
                    CONNECT_TIMEOUT_MILLIS,
                )
                WifiDirectSocketConnection(peer.connectionHandle, socket)
            } catch (e: IOException) {
                Logger.w(TAG, "failed to open Wi-Fi Direct socket", e)
                null
            }
        }
    }

    private suspend fun awaitGroupFormed(manager: WifiP2pManager, ch: WifiP2pManager.Channel): WifiP2pInfo? =
        withTimeoutOrNull(CONNECTION_INFO_TIMEOUT_MILLIS) {
            var info: WifiP2pInfo? = null
            while (info == null || !info!!.groupFormed) {
                info = suspendCancellableCoroutine { cont ->
                    manager.requestConnectionInfo(ch) { result -> if (cont.isActive) cont.resume(result) }
                }
                if (info?.groupFormed != true) delay(CONNECTION_INFO_POLL_MILLIS)
            }
            info
        }

    private fun ensureChannel(): WifiP2pManager.Channel? {
        channel?.let { return it }
        val manager = wifiP2pManager ?: return null
        val newChannel = manager.initialize(
            context,
            context.mainLooper,
            object : WifiP2pManager.ChannelListener {
                override fun onChannelDisconnected() {
                    Logger.w(TAG, "P2P channel disconnected")
                    channel = null
                }
            },
        )
        channel = newChannel
        return newChannel
    }

    private fun startAcceptLoopIfNeeded() {
        if (acceptJob != null) return
        acceptJob = applicationScope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket(SOCKET_PORT)
                serverSocket = server
                while (isActive) {
                    val socket = server.accept()
                    val handle = socket.inetAddress?.hostAddress ?: "wifidirect:${socket.port}"
                    _incomingConnections.tryEmit(WifiDirectSocketConnection(handle, socket))
                }
            } catch (e: IOException) {
                Logger.w(TAG, "Wi-Fi Direct accept loop stopped", e)
            }
        }
    }

    private class WifiDirectSocketConnection(
        override val remoteHandle: String,
        private val socket: Socket,
    ) : TransportConnection {
        override val kind: TransportKind = TransportKind.WIFI_DIRECT

        init {
            // A plain blocking Socket read has no suspension point, so kotlinx.coroutines
            // cancellation (e.g. SyncManager's withTimeoutOrNull around receiveOne) cannot
            // interrupt it — the collecting coroutine would simply block the IO-dispatcher
            // thread forever waiting for bytes that never arrive, and the "timeout" would
            // never actually fire. Socket.setSoTimeout makes the blocking read itself bounded
            // (throws SocketTimeoutException, an IOException, which incomingFrames already
            // catches below), so this can never hang past a bounded, real socket-level limit.
            socket.soTimeout = SOCKET_READ_TIMEOUT_MILLIS
        }

        private val output = DataOutputStream(socket.getOutputStream())
        private val input = DataInputStream(socket.getInputStream())

        override val incomingFrames: Flow<ByteArray> = flow {
            while (currentCoroutineContext().isActive) {
                val length = try {
                    input.readInt()
                } catch (e: IOException) {
                    Logger.d(TAG, "[WIFI_DIRECT] incomingFrames read stopped for $remoteHandle: ${e.javaClass.simpleName}")
                    break
                }
                if (length !in 0..ProtocolConstants.MAX_MESSAGE_BYTES) break
                val payload = ByteArray(length)
                try {
                    input.readFully(payload)
                } catch (e: IOException) {
                    Logger.d(TAG, "[WIFI_DIRECT] incomingFrames payload read stopped for $remoteHandle: ${e.javaClass.simpleName}")
                    break
                }
                emit(payload)
            }
        }.flowOn(Dispatchers.IO)

        override suspend fun send(message: ByteArray): Boolean = withContext(Dispatchers.IO) {
            try {
                output.writeInt(message.size)
                output.write(message)
                output.flush()
                true
            } catch (e: IOException) {
                false
            }
        }

        override fun close() {
            runCatching { socket.close() }
        }
    }

    private companion object {
        const val TAG = "WifiDirectTransport"
        const val SOCKET_PORT = 8988
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val CONNECTION_INFO_TIMEOUT_MILLIS = 8_000L
        const val CONNECTION_INFO_POLL_MILLIS = 400L

        // Bounds every blocking socket read so a stalled/vanished peer can't hang this
        // connection's incomingFrames forever (see WifiDirectSocketConnection's init).
        // Comfortably above SyncManager.RECEIVE_TIMEOUT_MILLIS (10s) so the socket-level
        // bound is never what fires first in the normal case.
        const val SOCKET_READ_TIMEOUT_MILLIS = 15_000
    }
}
