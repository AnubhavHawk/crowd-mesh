package com.crowdmesh.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.crowdmesh.data.local.entity.KnownPeerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KnownPeerDao {

    @Query("SELECT * FROM known_peers ORDER BY lastSeenAt DESC")
    fun observeAll(): Flow<List<KnownPeerEntity>>

    @Query("SELECT * FROM known_peers WHERE deviceId = :deviceId LIMIT 1")
    suspend fun get(deviceId: String): KnownPeerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KnownPeerEntity)

    @Query("DELETE FROM known_peers WHERE lastSeenAt <= :cutoffMillis")
    suspend fun deleteStale(cutoffMillis: Long): Int
}
