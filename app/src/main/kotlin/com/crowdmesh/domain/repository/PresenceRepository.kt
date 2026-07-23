package com.crowdmesh.domain.repository

import com.crowdmesh.domain.model.PresenceRecord
import kotlinx.coroutines.flow.Flow

/**
 * Source of truth for presence data: exactly one row per userId, replaced
 * (never appended to) on every update. Backed by Room ([data.repository.PresenceRepositoryImpl]).
 */
interface PresenceRepository {

    /** All non-expired records currently known, including our own — drives the heatmap. */
    fun observeAllRecords(): Flow<List<PresenceRecord>>

    fun observeOwnRecord(): Flow<PresenceRecord?>

    suspend fun getRecord(userId: String): PresenceRecord?

    /** Computes the next version for [userId] and persists a new [PresenceRecord], replacing any existing one. */
    suspend fun upsertOwnRecord(userId: String, geohash: String, timestampMillis: Long): PresenceRecord

    /**
     * Applies a record learned from a peer, resolving conflicts by "latest version wins".
     * Returns true if [record] was newer and was applied, false if it was ignored (stale/duplicate).
     */
    suspend fun mergeRemoteRecord(record: PresenceRecord, ttlExpiresAtMillis: Long): Boolean

    /** Most-recently-updated records, bounded to [limit] — used to build a gossip digest cheaply. */
    suspend fun recordsForDigest(limit: Int): List<PresenceRecord>

    /** Deletes records whose TTL has elapsed. Returns the number removed. */
    suspend fun pruneExpired(nowMillis: Long): Int
}
