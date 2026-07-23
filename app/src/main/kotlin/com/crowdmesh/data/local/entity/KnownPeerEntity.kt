package com.crowdmesh.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "known_peers")
data class KnownPeerEntity(
    @PrimaryKey val deviceId: String,
    val transportKind: String,
    val lastSeenAt: Long,
    val lastSyncedAt: Long?,
    val lastRssi: Int?,
)
