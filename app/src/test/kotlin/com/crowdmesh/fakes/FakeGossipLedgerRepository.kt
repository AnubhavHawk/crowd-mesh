package com.crowdmesh.fakes

import com.crowdmesh.domain.repository.GossipLedgerRepository

class FakeGossipLedgerRepository : GossipLedgerRepository {
    private val seen = mutableMapOf<String, Long>()

    override suspend fun hasSeen(messageId: String): Boolean = seen.containsKey(messageId)

    override suspend fun markSeen(messageId: String, receivedAtMillis: Long) {
        seen[messageId] = receivedAtMillis
    }

    override suspend fun pruneOlderThan(cutoffMillis: Long): Int {
        val toRemove = seen.filterValues { it <= cutoffMillis }.keys
        toRemove.forEach { seen.remove(it) }
        return toRemove.size
    }
}
