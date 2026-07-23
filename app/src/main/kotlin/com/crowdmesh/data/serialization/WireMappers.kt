package com.crowdmesh.data.serialization

import com.crowdmesh.domain.model.GossipMessage
import com.crowdmesh.domain.model.PresenceRecord

fun GossipMessage.toWireDto(): PresenceRecordWireDto = PresenceRecordWireDto(
    messageId = messageId,
    userId = record.userId,
    geohash = record.geohash,
    timestamp = record.timestamp,
    version = record.version,
    ttlExpiresAt = ttlExpiresAt,
    hopCount = hopCount,
)

fun PresenceRecordWireDto.toDomain(): GossipMessage = GossipMessage(
    messageId = messageId,
    record = PresenceRecord(userId = userId, geohash = geohash, timestamp = timestamp, version = version),
    ttlExpiresAt = ttlExpiresAt,
    hopCount = hopCount,
)
