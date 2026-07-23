package com.crowdmesh.mesh.transport

import com.crowdmesh.mesh.discovery.DiscoveredPeer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fans discovery/advertising out across every transport available on this
 * device and routes each connection attempt through whichever transport
 * actually discovered that peer.
 *
 * Known simplification: since each radio has its own addressing scheme,
 * there's no cross-transport correlation of "this BLE peer and this Wi-Fi
 * Aware peer are the same physical phone" — if a peer is reachable over two
 * transports we may end up gossiping with them twice, once per transport.
 * That's wasteful but harmless (gossip merges/dedups are idempotent); a
 * production version would exchange transport-agnostic identity early and
 * de-duplicate by that instead.
 */
@Singleton
class TransportManager @Inject constructor(
    bleTransport: BleTransport,
    wifiAwareTransport: WifiAwareTransport,
    wifiDirectTransport: WifiDirectTransport,
) {
    private val allTransports: List<Transport> = listOf(bleTransport, wifiAwareTransport, wifiDirectTransport)

    val availableTransports: List<Transport> get() = allTransports.filter { it.isAvailable() }

    val incomingConnections: Flow<TransportConnection> = allTransports.map { it.incomingConnections }.merge()

    fun startAdvertisingAll(identityPayload: ByteArray) {
        availableTransports.forEach { it.startAdvertising(identityPayload) }
    }

    fun stopAdvertisingAll() {
        allTransports.forEach { it.stopAdvertising() }
    }

    fun discoverPeers(dutyCycle: MeshDutyCycle): Flow<DiscoveredPeer> =
        availableTransports.map { it.discoverPeers(dutyCycle) }.merge()

    suspend fun openConnection(peer: DiscoveredPeer): TransportConnection? {
        val transport = allTransports.firstOrNull { it.kind == peer.transportKind } ?: return null
        return transport.openConnection(peer)
    }
}
