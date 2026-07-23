package com.crowdmesh.data.repository

import com.crowdmesh.data.local.entity.KnownPeerEntity
import com.crowdmesh.data.local.entity.PresenceRecordEntity
import com.crowdmesh.domain.model.KnownPeer
import com.crowdmesh.domain.model.PresenceRecord
import com.crowdmesh.domain.model.TransportKind

fun PresenceRecordEntity.toDomain(): PresenceRecord = PresenceRecord(
    userId = userId,
    geohash = geohash,
    timestamp = timestamp,
    version = version,
)

fun PresenceRecord.toEntity(ttlExpiresAtMillis: Long): PresenceRecordEntity = PresenceRecordEntity(
    userId = userId,
    geohash = geohash,
    timestamp = timestamp,
    version = version,
    ttlExpiresAt = ttlExpiresAtMillis,
)

fun KnownPeerEntity.toDomain(): KnownPeer = KnownPeer(
    deviceId = deviceId,
    transportKind = TransportKind.valueOf(transportKind),
    lastSeenAt = lastSeenAt,
    lastSyncedAt = lastSyncedAt,
    lastRssi = lastRssi,
)

fun KnownPeer.toEntity(): KnownPeerEntity = KnownPeerEntity(
    deviceId = deviceId,
    transportKind = transportKind.name,
    lastSeenAt = lastSeenAt,
    lastSyncedAt = lastSyncedAt,
    lastRssi = lastRssi,
)
