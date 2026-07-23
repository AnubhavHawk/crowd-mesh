package com.crowdmesh.mesh.transport

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.DiscoverySession
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareSession
import android.os.Build
import androidx.annotation.RequiresApi
import com.crowdmesh.di.ApplicationScope
import com.crowdmesh.domain.model.TransportKind
import com.crowdmesh.mesh.ble.MessageFramer
import com.crowdmesh.mesh.discovery.DiscoveredPeer
import com.crowdmesh.util.Logger
import com.crowdmesh.util.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secondary transport using Wi-Fi Aware (NAN) — only available on hardware
 * that declares [PackageManager.FEATURE_WIFI_AWARE] (mostly Pixel-class
 * devices). Intentionally "thinner" than [com.crowdmesh.mesh.transport.BleTransport]:
 * it exchanges mesh frames directly as Aware discovery messages
 * (`sendMessage`, ~250 bytes/message) instead of standing up a full
 * Wi-Fi Aware data-path network specifier + socket — message-based
 * exchange is more than enough bandwidth for gossiped presence records and
 * is far less code to get right.
 */
@Singleton
class WifiAwareTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : Transport {

    override val kind: TransportKind = TransportKind.WIFI_AWARE

    private val wifiAwareManager: WifiAwareManager? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
        } else {
            null
        }
    }

    private var awareSession: WifiAwareSession? = null

    // Populated by onServiceDiscovered on the *subscribe* session, since that is
    // the only side that ever learns a peer's handle in this thinner implementation.
    private var subscribeSession: SubscribeDiscoverySession? = null
    private var publishSession: PublishDiscoverySession? = null

    private val peerHandlesByAddress = ConcurrentHashMap<String, PeerHandle>()
    private val messageIdSequence = AtomicInteger(1)
    private val pendingSendAcks = ConcurrentHashMap<Int, CompletableDeferred<Boolean>>()

    private val _incomingConnections = MutableSharedFlow<TransportConnection>(extraBufferCapacity = 8)
    override val incomingConnections: SharedFlow<TransportConnection> = _incomingConnections.asSharedFlow()

    private val _incomingMessages = MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 64)

    override fun isAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE) &&
            wifiAwareManager?.isAvailable == true

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    override fun startAdvertising(identityPayload: ByteArray) {
        if (!isAvailable()) return
        attachIfNeeded { session ->
            val config = PublishConfig.Builder()
                .setServiceName(SERVICE_NAME)
                .setServiceSpecificInfo(identityPayload)
                .build()
            session.publish(config, object : DiscoverySessionCallback() {
                override fun onPublishStarted(session: PublishDiscoverySession) {
                    publishSession = session
                }

                override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                    onPeerMessage(peerHandle, message)
                }

                override fun onMessageSendSucceeded(messageId: Int) {
                    pendingSendAcks.remove(messageId)?.complete(true)
                }

                override fun onMessageSendFailed(messageId: Int) {
                    pendingSendAcks.remove(messageId)?.complete(false)
                }
            }, null)
        }
    }

    override fun stopAdvertising() {
        publishSession?.close()
        publishSession = null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    override fun discoverPeers(dutyCycle: MeshDutyCycle): Flow<DiscoveredPeer> = callbackFlow {
        if (!isAvailable()) {
            close()
            return@callbackFlow
        }
        attachIfNeeded { session ->
            val config = SubscribeConfig.Builder()
                .setServiceName(SERVICE_NAME)
                .build()
            session.subscribe(config, object : DiscoverySessionCallback() {
                override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                    subscribeSession = session
                }

                override fun onServiceDiscovered(
                    peerHandle: PeerHandle,
                    serviceSpecificInfo: ByteArray?,
                    matchFilter: MutableList<ByteArray>?,
                ) {
                    val syntheticAddress = "aware:${peerHandle.hashCode()}"
                    peerHandlesByAddress[syntheticAddress] = peerHandle
                    trySend(
                        DiscoveredPeer(
                            connectionHandle = syntheticAddress,
                            transportKind = TransportKind.WIFI_AWARE,
                            rssi = null,
                            timestampMillis = timeProvider.nowMillis(),
                        )
                    )
                }

                override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                    onPeerMessage(peerHandle, message)
                }

                override fun onMessageSendSucceeded(messageId: Int) {
                    pendingSendAcks.remove(messageId)?.complete(true)
                }

                override fun onMessageSendFailed(messageId: Int) {
                    pendingSendAcks.remove(messageId)?.complete(false)
                }
            }, null)
        }
        awaitClose {
            subscribeSession?.close()
            subscribeSession = null
        }
    }

    override suspend fun openConnection(peer: DiscoveredPeer): TransportConnection? {
        val peerHandle = peerHandlesByAddress[peer.connectionHandle] ?: return null
        val session = subscribeSession ?: return null
        return WifiAwareConnection(peer.connectionHandle, peerHandle, session)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun attachIfNeeded(onAttached: (WifiAwareSession) -> Unit) {
        awareSession?.let { onAttached(it); return }
        val manager = wifiAwareManager ?: return
        manager.attach(object : AttachCallback() {
            override fun onAttached(session: WifiAwareSession) {
                awareSession = session
                onAttached(session)
            }

            override fun onAttachFailed() {
                Logger.w(TAG, "Wi-Fi Aware attach failed")
            }
        }, null)
    }

    private fun onPeerMessage(peerHandle: PeerHandle, message: ByteArray) {
        val existingAddress = peerHandlesByAddress.entries.firstOrNull { it.value == peerHandle }?.key
        val syntheticAddress = existingAddress ?: "aware:${peerHandle.hashCode()}".also {
            peerHandlesByAddress[it] = peerHandle
        }

        // If we didn't discover this peer ourselves (existingAddress == null), they found
        // us via our publish and this is the first we're hearing from them — surface it as
        // an incoming connection so SyncManager can respond, mirroring BleTransport's server side.
        if (existingAddress == null) {
            val session = publishSession
            if (session != null) {
                _incomingConnections.tryEmit(WifiAwareConnection(syntheticAddress, peerHandle, session))
            }
        }

        applicationScope.launch {
            _incomingMessages.emit(syntheticAddress to message)
        }
    }

    private inner class WifiAwareConnection(
        override val remoteHandle: String,
        private val peerHandle: PeerHandle,
        private val session: DiscoverySession,
    ) : TransportConnection {
        override val kind: TransportKind = TransportKind.WIFI_AWARE

        override val incomingFrames: Flow<ByteArray> = _incomingMessages
            .filter { (address, _) -> address == remoteHandle }
            .map { (_, payload) -> payload }

        @SuppressLint("MissingPermission")
        override suspend fun send(message: ByteArray): Boolean {
            for (chunk in MessageFramer.encodeChunks(message, chunkSize = AWARE_MESSAGE_CHUNK_BYTES)) {
                val messageId = messageIdSequence.getAndIncrement()
                val ack = CompletableDeferred<Boolean>()
                pendingSendAcks[messageId] = ack
                session.sendMessage(peerHandle, messageId, chunk)
                val delivered = withTimeoutOrNull(SEND_TIMEOUT_MILLIS) { ack.await() } ?: false
                pendingSendAcks.remove(messageId)
                if (!delivered) return false
            }
            return true
        }

        override fun close() {
            // No persistent socket/network specifier is held in this message-based
            // implementation; the shared discovery session stays open for other peers.
        }
    }

    private companion object {
        const val TAG = "WifiAwareTransport"
        const val SERVICE_NAME = "crowdmesh"
        const val AWARE_MESSAGE_CHUNK_BYTES = 200
        const val SEND_TIMEOUT_MILLIS = 5_000L
    }
}
