package com.crowdmesh.domain.repository

/**
 * Persisted set of gossip message IDs already seen, so the mesh can ignore
 * duplicates it has already applied or relayed. Backing store for
 * [com.crowdmesh.mesh.sync.MessageStore]'s durable dedup layer.
 */
interface GossipLedgerRepository {

    suspend fun hasSeen(messageId: String): Boolean

    suspend fun markSeen(messageId: String, receivedAtMillis: Long)

    suspend fun pruneOlderThan(cutoffMillis: Long): Int
}
