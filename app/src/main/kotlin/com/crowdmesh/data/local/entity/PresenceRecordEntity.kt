package com.crowdmesh.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Exactly one row per user — [userId] is the primary key and every write is
 * a REPLACE, never an append. No history table exists anywhere in the app.
 */
@Entity(tableName = "presence_records")
data class PresenceRecordEntity(
    @PrimaryKey val userId: String,
    val geohash: String,
    val timestamp: Long,
    val version: Long,
    val ttlExpiresAt: Long,
)
