package com.crowdmesh.domain.usecase

import com.crowdmesh.domain.repository.GossipLedgerRepository
import com.crowdmesh.domain.repository.PeerRepository
import com.crowdmesh.domain.repository.PresenceRepository
import com.crowdmesh.util.TimeProvider
import javax.inject.Inject

/** Runs from [com.crowdmesh.work.ExpiredRecordCleanupWorker] to keep local storage bounded. */
class PruneExpiredRecordsUseCase @Inject constructor(
    private val presenceRepository: PresenceRepository,
    private val gossipLedgerRepository: GossipLedgerRepository,
    private val peerRepository: PeerRepository,
    private val timeProvider: TimeProvider,
) {
    data class Result(
        val recordsRemoved: Int,
        val ledgerEntriesRemoved: Int,
        val stalePeersRemoved: Int,
    )

    suspend operator fun invoke(): Result {
        val now = timeProvider.nowMillis()
        val recordsRemoved = presenceRepository.pruneExpired(now)
        val ledgerRemoved = gossipLedgerRepository.pruneOlderThan(now - LEDGER_RETENTION_MILLIS)
        val peersRemoved = peerRepository.pruneStale(now, STALE_PEER_MILLIS)
        return Result(recordsRemoved, ledgerRemoved, peersRemoved)
    }

    private companion object {
        // Only needs to outlast the longest plausible gap between gossip encounters
        // with the same peer, not full record TTL — it's purely a relay-dedup memory.
        const val LEDGER_RETENTION_MILLIS = 60 * 60 * 1000L
        const val STALE_PEER_MILLIS = 24 * 60 * 60 * 1000L
    }
}
