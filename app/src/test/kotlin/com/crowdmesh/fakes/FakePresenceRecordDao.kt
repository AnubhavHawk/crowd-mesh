package com.crowdmesh.fakes

import com.crowdmesh.data.local.dao.PresenceRecordDao
import com.crowdmesh.data.local.entity.PresenceRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory stand-in for Room so repository/use-case tests don't need an instrumented environment. */
class FakePresenceRecordDao : PresenceRecordDao {
    private val table = MutableStateFlow<Map<String, PresenceRecordEntity>>(emptyMap())

    override fun observeAll(): Flow<List<PresenceRecordEntity>> =
        table.map { it.values.sortedByDescending { entity -> entity.timestamp } }

    override fun observe(userId: String): Flow<PresenceRecordEntity?> = table.map { it[userId] }

    override suspend fun get(userId: String): PresenceRecordEntity? = table.value[userId]

    override suspend fun upsert(entity: PresenceRecordEntity) {
        table.value = table.value + (entity.userId to entity)
    }

    override suspend fun mostRecent(limit: Int): List<PresenceRecordEntity> =
        table.value.values.sortedByDescending { it.timestamp }.take(limit)

    override suspend fun deleteExpired(nowMillis: Long): Int {
        val expired = table.value.filterValues { it.ttlExpiresAt <= nowMillis }
        table.value = table.value - expired.keys
        return expired.size
    }
}
