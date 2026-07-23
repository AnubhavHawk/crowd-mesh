package com.crowdmesh.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.crowdmesh.data.local.entity.PresenceRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PresenceRecordDao {

    @Query("SELECT * FROM presence_records ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<PresenceRecordEntity>>

    @Query("SELECT * FROM presence_records WHERE userId = :userId LIMIT 1")
    fun observe(userId: String): Flow<PresenceRecordEntity?>

    @Query("SELECT * FROM presence_records WHERE userId = :userId LIMIT 1")
    suspend fun get(userId: String): PresenceRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PresenceRecordEntity)

    @Query("SELECT * FROM presence_records ORDER BY timestamp DESC LIMIT :limit")
    suspend fun mostRecent(limit: Int): List<PresenceRecordEntity>

    @Query("DELETE FROM presence_records WHERE ttlExpiresAt <= :nowMillis")
    suspend fun deleteExpired(nowMillis: Long): Int
}
