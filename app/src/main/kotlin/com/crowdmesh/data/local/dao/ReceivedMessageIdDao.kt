package com.crowdmesh.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.crowdmesh.data.local.entity.ReceivedMessageIdEntity

@Dao
interface ReceivedMessageIdDao {

    @Query("SELECT EXISTS(SELECT 1 FROM received_message_ids WHERE messageId = :messageId)")
    suspend fun exists(messageId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ReceivedMessageIdEntity)

    @Query("DELETE FROM received_message_ids WHERE receivedAt <= :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int
}
