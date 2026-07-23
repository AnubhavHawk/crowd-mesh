package com.crowdmesh.data.serialization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketCodecTest {

    @Test
    fun `hello round-trips`() {
        val hello = HelloDto(deviceId = "device-123", protocolVersion = 1)
        val decoded = PacketCodec.decode(PacketCodec.encodeHello(hello))
        assertTrue(decoded is PacketCodec.DecodedPacket.Hello)
        assertEquals(hello, (decoded as PacketCodec.DecodedPacket.Hello).value)
    }

    @Test
    fun `digest round-trips with multiple entries`() {
        val digest = DigestDto(
            entries = listOf(
                DigestEntryDto("user-a", 1L),
                DigestEntryDto("user-b", 42L),
            ),
        )
        val decoded = PacketCodec.decode(PacketCodec.encodeDigest(digest))
        assertTrue(decoded is PacketCodec.DecodedPacket.Digest)
        assertEquals(digest, (decoded as PacketCodec.DecodedPacket.Digest).value)
    }

    @Test
    fun `empty digest round-trips`() {
        val digest = DigestDto(entries = emptyList())
        val decoded = PacketCodec.decode(PacketCodec.encodeDigest(digest)) as PacketCodec.DecodedPacket.Digest
        assertTrue(decoded.value.entries.isEmpty())
    }

    @Test
    fun `record request round-trips`() {
        val request = RecordRequestDto(userIds = listOf("a", "b", "c"))
        val decoded = PacketCodec.decode(PacketCodec.encodeRecordRequest(request))
        assertEquals(request, (decoded as PacketCodec.DecodedPacket.RecordRequest).value)
    }

    @Test
    fun `record batch round-trips`() {
        val batch = RecordBatchDto(
            records = listOf(
                PresenceRecordWireDto(
                    messageId = "user-a:1",
                    userId = "user-a",
                    geohash = "u4pruyd0",
                    timestamp = 1_700_000_000_000L,
                    version = 1L,
                    ttlExpiresAt = 1_700_001_800_000L,
                    hopCount = 0,
                ),
            ),
        )
        val decoded = PacketCodec.decode(PacketCodec.encodeRecordBatch(batch))
        assertEquals(batch, (decoded as PacketCodec.DecodedPacket.RecordBatch).value)
    }

    @Test
    fun `bye round-trips with no payload`() {
        val decoded = PacketCodec.decode(PacketCodec.encodeBye())
        assertTrue(decoded is PacketCodec.DecodedPacket.Bye)
    }

    @Test
    fun `decoding an empty byte array throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            PacketCodec.decode(ByteArray(0))
        }
    }

    @Test
    fun `decoding an unknown packet type throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            PacketCodec.decode(byteArrayOf(99))
        }
    }
}
