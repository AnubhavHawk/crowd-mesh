package com.crowdmesh.mesh.peer

import com.crowdmesh.domain.model.KnownPeer
import com.crowdmesh.domain.repository.PeerRepository
import com.crowdmesh.mesh.discovery.DiscoveredPeer
import com.crowdmesh.util.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton

/** Decides which discovered peers are worth spending a connection on, and keeps [PeerRepository] current. */
@Singleton
class PeerManager @Inject constructor(
    private val peerRepository: PeerRepository,
    private val timeProvider: TimeProvider,
) {
    /** Avoid re-syncing with the same peer every single scan burst while we're still in range of them. */
    suspend fun shouldSync(peer: DiscoveredPeer): Boolean {
        val lastSyncedAt = peerRepository.getPeer(peer.connectionHandle)?.lastSyncedAt ?: return true
        return timeProvider.nowMillis() - lastSyncedAt >= RESYNC_COOLDOWN_MILLIS
    }

    suspend fun recordSighting(peer: DiscoveredPeer) {
        val existing = peerRepository.getPeer(peer.connectionHandle)
        peerRepository.upsertPeer(
            KnownPeer(
                deviceId = peer.connectionHandle,
                transportKind = peer.transportKind,
                lastSeenAt = peer.timestampMillis,
                lastSyncedAt = existing?.lastSyncedAt,
                lastRssi = peer.rssi,
            )
        )
    }

    suspend fun recordSynced(peer: DiscoveredPeer) {
        val existing = peerRepository.getPeer(peer.connectionHandle)
        val base = existing ?: KnownPeer(
            deviceId = peer.connectionHandle,
            transportKind = peer.transportKind,
            lastSeenAt = peer.timestampMillis,
            lastSyncedAt = null,
            lastRssi = peer.rssi,
        )
        peerRepository.upsertPeer(base.copy(lastSyncedAt = timeProvider.nowMillis()))
    }

    private companion object {
        const val RESYNC_COOLDOWN_MILLIS = 60_000L
    }
}
