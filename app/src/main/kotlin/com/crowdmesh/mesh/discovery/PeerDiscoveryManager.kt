package com.crowdmesh.mesh.discovery

import com.crowdmesh.mesh.transport.MeshDutyCycle
import com.crowdmesh.mesh.transport.TransportManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin facade [com.crowdmesh.mesh.MeshEngine] uses for discovery/advertising
 * so it doesn't need to know about individual transports — everything here
 * delegates straight to [TransportManager], which fans out across whichever
 * radios are actually available on this device.
 */
@Singleton
class PeerDiscoveryManager @Inject constructor(
    private val transportManager: TransportManager,
) {
    fun discover(dutyCycle: MeshDutyCycle): Flow<DiscoveredPeer> = transportManager.discoverPeers(dutyCycle)

    fun startAdvertising(identityPayload: ByteArray) = transportManager.startAdvertisingAll(identityPayload)

    fun stopAdvertising() = transportManager.stopAdvertisingAll()
}
