package com.crowdmesh.data.serialization

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Wire-format DTOs (compact ProtoBuf via kotlinx.serialization) exchanged
 * once two devices have a live BLE/Wi-Fi connection. Kept separate from the
 * `domain.model` types so domain stays free of serialization annotations.
 */
@Serializable
data class HelloDto(
    @ProtoNumber(1) val deviceId: String,
    @ProtoNumber(2) val protocolVersion: Int,
)

@Serializable
data class DigestEntryDto(
    @ProtoNumber(1) val userId: String,
    @ProtoNumber(2) val version: Long,
)

@Serializable
data class DigestDto(
    @ProtoNumber(1) val entries: List<DigestEntryDto>,
)

/** Sent in response to a peer's digest: "send me full records for these userIds". */
@Serializable
data class RecordRequestDto(
    @ProtoNumber(1) val userIds: List<String>,
)

@Serializable
data class PresenceRecordWireDto(
    @ProtoNumber(1) val messageId: String,
    @ProtoNumber(2) val userId: String,
    @ProtoNumber(3) val geohash: String,
    @ProtoNumber(4) val timestamp: Long,
    @ProtoNumber(5) val version: Long,
    @ProtoNumber(6) val ttlExpiresAt: Long,
    @ProtoNumber(7) val hopCount: Int,
)

@Serializable
data class RecordBatchDto(
    @ProtoNumber(1) val records: List<PresenceRecordWireDto>,
)
