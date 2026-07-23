package com.crowdmesh.mesh.peer

import com.crowdmesh.mesh.discovery.DiscoveredPeer
import com.crowdmesh.mesh.transport.TransportConnection
import com.crowdmesh.mesh.transport.TransportManager
import com.crowdmesh.util.Logger
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges [PeerManager]'s "is this worth it" decision to an actual
 * [TransportConnection], capping how many run concurrently — Android's BLE
 * stack only reliably supports a handful of simultaneous GATT connections.
 */
@Singleton
class ConnectionManager @Inject constructor(
    private val transportManager: TransportManager,
    private val peerManager: PeerManager,
) {
    private val connectionSlots = Semaphore(MAX_CONCURRENT_CONNECTIONS)

    suspend fun connectIfWorthwhile(peer: DiscoveredPeer, onConnected: suspend (TransportConnection) -> Unit) {
        peerManager.recordSighting(peer)
        if (!peerManager.shouldSync(peer)) return

        connectionSlots.withPermit {
            val connection = transportManager.openConnection(peer)
            if (connection == null) {
                Logger.d(TAG, "could not open connection to ${peer.connectionHandle}")
                return@withPermit
            }
            try {
                onConnected(connection)
                peerManager.recordSynced(peer)
            } finally {
                connection.close()
            }
        }
    }

    private companion object {
        const val TAG = "ConnectionManager"
        const val MAX_CONCURRENT_CONNECTIONS = 3
    }
}
