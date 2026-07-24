package com.crowdmesh.mesh.transport

import com.crowdmesh.di.ApplicationScope
import com.crowdmesh.domain.model.TransportKind
import com.crowdmesh.mesh.ble.BleAdvertiser
import com.crowdmesh.mesh.ble.BleGattClientManager
import com.crowdmesh.mesh.ble.BleGattServerManager
import com.crowdmesh.mesh.ble.AdaptiveScanScheduler
import com.crowdmesh.mesh.ble.ScanCadence
import com.crowdmesh.mesh.discovery.DiscoveredPeer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The primary, fully-implemented mesh transport — works on any API 26+
 * device with a Bluetooth radio, no special hardware required.
 */
@Singleton
class BleTransport @Inject constructor(
    private val advertiser: BleAdvertiser,
    private val scanScheduler: AdaptiveScanScheduler,
    private val gattClientManager: BleGattClientManager,
    private val gattServerManager: BleGattServerManager,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : Transport {

    override val kind: TransportKind = TransportKind.BLE

    private val _incomingConnections = MutableSharedFlow<TransportConnection>(extraBufferCapacity = 8)
    override val incomingConnections: SharedFlow<TransportConnection> = _incomingConnections.asSharedFlow()

    init {
        // Only wires the flow collection — no Bluetooth API call here. Hilt may
        // construct this object (and thus run this init block) before runtime
        // permissions are granted, so anything touching the radio must wait for
        // start() instead. See the Transport.start() doc for why.
        applicationScope.launch {
            gattServerManager.deviceConnected.collect { address ->
                _incomingConnections.tryEmit(BleServerConnection(address, gattServerManager))
            }
        }
    }

    override fun start() = gattServerManager.start()

    override fun isAvailable(): Boolean = advertiser.isSupported()

    override fun startAdvertising(identityPayload: ByteArray) = advertiser.start(identityPayload)

    override fun stopAdvertising() = advertiser.stop()

    override fun discoverPeers(dutyCycle: MeshDutyCycle): Flow<DiscoveredPeer> =
        scanScheduler.discoverPeers(dutyCycle.toScanCadence())

    override suspend fun openConnection(peer: DiscoveredPeer): TransportConnection? {
        val session = gattClientManager.connect(peer.connectionHandle) ?: return null
        return BleClientConnection(peer.connectionHandle, session)
    }

    private fun MeshDutyCycle.toScanCadence(): ScanCadence = when (this) {
        MeshDutyCycle.ACTIVE -> ScanCadence.ACTIVE
        MeshDutyCycle.FOREGROUND_IDLE -> ScanCadence.FOREGROUND_IDLE
        MeshDutyCycle.BACKGROUND_BURST -> ScanCadence.BACKGROUND_BURST
    }

    private class BleClientConnection(
        override val remoteHandle: String,
        private val session: BleGattClientManager.Session,
    ) : TransportConnection {
        override val kind: TransportKind = TransportKind.BLE
        override val incomingFrames: Flow<ByteArray> = session.incomingFrames
        override suspend fun send(message: ByteArray): Boolean = session.send(message)
        override fun close() = session.close()
    }

    private class BleServerConnection(
        override val remoteHandle: String,
        private val serverManager: BleGattServerManager,
    ) : TransportConnection {
        override val kind: TransportKind = TransportKind.BLE
        override val incomingFrames: Flow<ByteArray> = serverManager.framesFor(remoteHandle) ?: emptyFlow()
        override suspend fun send(message: ByteArray): Boolean = serverManager.sendFrame(remoteHandle, message)
        override fun close() {
            // The peripheral role doesn't proactively tear down a central's connection;
            // it simply stops responding. The central will time out and disconnect.
        }
    }
}
