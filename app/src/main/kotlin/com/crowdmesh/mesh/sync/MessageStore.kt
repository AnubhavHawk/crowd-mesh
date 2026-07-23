package com.crowdmesh.mesh.sync

import com.crowdmesh.domain.GossipPolicy
import com.crowdmesh.domain.model.GossipMessage
import com.crowdmesh.domain.repository.GossipLedgerRepository
import com.crowdmesh.util.TimeProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gossip dedup: a small in-memory LRU for the hot path plus the durable
 * [GossipLedgerRepository] ledger so dedup survives process death. A
 * message is only ever processed once, is dropped once expired, and is
 * dropped if its hop count already exceeds [GossipPolicy.MAX_HOPS].
 */
@Singleton
class MessageStore @Inject constructor(
    private val gossipLedgerRepository: GossipLedgerRepository,
    private val timeProvider: TimeProvider,
) {
    private val mutex = Mutex()
    private val recentlySeen = object : LinkedHashMap<String, Boolean>(INITIAL_CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>): Boolean =
            size > MAX_IN_MEMORY_ENTRIES
    }

    suspend fun shouldProcess(message: GossipMessage): Boolean {
        if (message.isExpired(timeProvider.nowMillis())) return false
        if (!message.canRelayFurther(GossipPolicy.MAX_HOPS)) return false
        return !hasSeen(message.messageId)
    }

    suspend fun markProcessed(message: GossipMessage) {
        mutex.withLock { recentlySeen[message.messageId] = true }
        gossipLedgerRepository.markSeen(message.messageId, timeProvider.nowMillis())
    }

    private suspend fun hasSeen(messageId: String): Boolean {
        val inMemory = mutex.withLock { recentlySeen.containsKey(messageId) }
        return inMemory || gossipLedgerRepository.hasSeen(messageId)
    }

    private companion object {
        const val MAX_IN_MEMORY_ENTRIES = 2048
        const val INITIAL_CAPACITY = 64
    }
}
