package com.crowdmesh.data.serialization

import com.crowdmesh.mesh.protocol.PacketType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf

/**
 * Encodes/decodes a single logical mesh message as `[1-byte PacketType][ProtoBuf payload]`.
 * This is transport-agnostic: the BLE GATT layer is responsible for
 * chunking a given byte array across MTU-sized writes and reassembling it
 * on the other side (see `mesh.ble.MessageFramer`) before handing the whole
 * thing back to [decode].
 */
@OptIn(ExperimentalSerializationApi::class)
object PacketCodec {

    sealed interface DecodedPacket {
        data class Hello(val value: HelloDto) : DecodedPacket
        data class Digest(val value: DigestDto) : DecodedPacket
        data class RecordRequest(val value: RecordRequestDto) : DecodedPacket
        data class RecordBatch(val value: RecordBatchDto) : DecodedPacket
        data object Bye : DecodedPacket
    }

    fun encodeHello(hello: HelloDto): ByteArray = frame(PacketType.HELLO, ProtoBuf.encodeToByteArray(hello))

    fun encodeDigest(digest: DigestDto): ByteArray = frame(PacketType.DIGEST, ProtoBuf.encodeToByteArray(digest))

    fun encodeRecordRequest(request: RecordRequestDto): ByteArray =
        frame(PacketType.RECORD_REQUEST, ProtoBuf.encodeToByteArray(request))

    fun encodeRecordBatch(batch: RecordBatchDto): ByteArray =
        frame(PacketType.RECORD_BATCH, ProtoBuf.encodeToByteArray(batch))

    fun encodeBye(): ByteArray = frame(PacketType.BYE, ByteArray(0))

    fun decode(bytes: ByteArray): DecodedPacket {
        require(bytes.isNotEmpty()) { "cannot decode an empty packet" }
        val typeId = bytes[0]
        val payload = bytes.copyOfRange(1, bytes.size)
        val type = PacketType.fromId(typeId)
            ?: throw IllegalArgumentException("Unknown packet type byte: $typeId")

        return when (type) {
            PacketType.HELLO -> DecodedPacket.Hello(ProtoBuf.decodeFromByteArray(payload))
            PacketType.DIGEST -> DecodedPacket.Digest(ProtoBuf.decodeFromByteArray(payload))
            PacketType.RECORD_REQUEST -> DecodedPacket.RecordRequest(ProtoBuf.decodeFromByteArray(payload))
            PacketType.RECORD_BATCH -> DecodedPacket.RecordBatch(ProtoBuf.decodeFromByteArray(payload))
            PacketType.BYE -> DecodedPacket.Bye
        }
    }

    private fun frame(type: PacketType, payload: ByteArray): ByteArray {
        val out = ByteArray(1 + payload.size)
        out[0] = type.id
        payload.copyInto(out, destinationOffset = 1)
        return out
    }
}
