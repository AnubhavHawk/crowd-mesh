package com.crowdmesh.data.repository

import com.crowdmesh.data.local.dao.ReceivedMessageIdDao
import com.crowdmesh.data.local.entity.ReceivedMessageIdEntity
import com.crowdmesh.domain.repository.GossipLedgerRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GossipLedgerRepositoryImpl @Inject constructor(
    private val dao: ReceivedMessageIdDao,
) : GossipLedgerRepository {

    override suspend fun hasSeen(messageId: String): Boolean = dao.exists(messageId)

    override suspend fun markSeen(messageId: String, receivedAtMillis: Long) =
        dao.insert(ReceivedMessageIdEntity(messageId = messageId, receivedAt = receivedAtMillis))

    override suspend fun pruneOlderThan(cutoffMillis: Long): Int = dao.deleteOlderThan(cutoffMillis)
}
