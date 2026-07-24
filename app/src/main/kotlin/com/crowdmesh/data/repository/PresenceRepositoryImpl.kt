package com.crowdmesh.data.repository

import com.crowdmesh.data.local.dao.PresenceRecordDao
import com.crowdmesh.domain.GossipPolicy
import com.crowdmesh.domain.model.PresenceRecord
import com.crowdmesh.domain.repository.IdentityProvider
import com.crowdmesh.domain.repository.PresenceRepository
import com.crowdmesh.util.Logger
import com.crowdmesh.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresenceRepositoryImpl @Inject constructor(
    private val dao: PresenceRecordDao,
    private val identityProvider: IdentityProvider,
    private val timeProvider: TimeProvider,
) : PresenceRepository {

    override fun observeAllRecords(): Flow<List<PresenceRecord>> =
        combine(dao.observeAll(), tickerFlow(LIVE_DECAY_TICK_MILLIS)) { entities, _ -> entities }
            .map { entities ->
                val now = timeProvider.nowMillis()
                val (live, expired) = entities.partition { it.ttlExpiresAt > now }
                Logger.d(
                    TAG,
                    "[DB] observeAllRecords emitting ${live.size} live record(s) (${entities.size} total in table, " +
                        "${expired.size} expired and filtered out: ${expired.map { it.userId }})",
                )
                live.forEach {
                    Logger.d(
                        TAG,
                        "[DB] record userId=${it.userId} geohash=${it.geohash} timestamp=${it.timestamp} " +
                            "ttlExpiresAt=${it.ttlExpiresAt} version=${it.version}",
                    )
                }
                live.map { it.toDomain() }
            }

    override fun observeOwnRecord(): Flow<PresenceRecord?> =
        flow { emit(identityProvider.getOrCreateDeviceId()) }
            .flatMapLatest { userId -> dao.observe(userId) }
            .map { it?.toDomain() }

    override suspend fun getRecord(userId: String): PresenceRecord? = dao.get(userId)?.toDomain()

    override suspend fun upsertOwnRecord(userId: String, geohash: String, timestampMillis: Long): PresenceRecord {
        val previous = dao.get(userId)
        val nextVersion = (previous?.version ?: 0L) + 1L
        val record = PresenceRecord(
            userId = userId,
            geohash = geohash,
            timestamp = timestampMillis,
            version = nextVersion,
        )
        dao.upsert(record.toEntity(ttlExpiresAtMillis = timestampMillis + GossipPolicy.RECORD_TTL_MILLIS))
        return record
    }

    override suspend fun mergeRemoteRecord(record: PresenceRecord, ttlExpiresAtMillis: Long): Boolean {
        val existing = dao.get(record.userId)
        val isNewer = existing == null ||
            record.version > existing.version ||
            (record.version == existing.version && record.timestamp > existing.timestamp)

        if (isNewer) {
            dao.upsert(record.toEntity(ttlExpiresAtMillis))
            Logger.d(TAG, "[DB] mergeRemoteRecord upserted ${record.userId} v${record.version} (existing was ${existing?.version})")
        } else {
            Logger.d(TAG, "[DB] mergeRemoteRecord rejected ${record.userId} v${record.version} as stale (existing=${existing?.version})")
        }
        return isNewer
    }

    override suspend fun recordsForDigest(limit: Int): List<PresenceRecord> =
        dao.mostRecent(limit.coerceAtMost(GossipPolicy.DIGEST_ENTRY_LIMIT)).map { it.toDomain() }

    override suspend fun pruneExpired(nowMillis: Long): Int = dao.deleteExpired(nowMillis)

    private fun tickerFlow(intervalMillis: Long): Flow<Unit> = flow {
        while (currentCoroutineContext().isActive) {
            emit(Unit)
            delay(intervalMillis)
        }
    }

    private companion object {
        const val TAG = "PresenceRepository"

        // Room's Flow re-emits on table writes only; this tick makes TTL-based
        // decay (heatmap fading, expired-cell drop) visible even when nothing
        // else is being written to the table.
        const val LIVE_DECAY_TICK_MILLIS = 30_000L
    }
}
