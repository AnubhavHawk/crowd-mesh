package com.crowdmesh.mesh.ble

import com.crowdmesh.mesh.protocol.ProtocolConstants
import java.io.ByteArrayOutputStream

/**
 * BLE GATT writes/notifications are capped by the negotiated ATT MTU, so a
 * single logical mesh message (a whole [com.crowdmesh.data.serialization.PacketCodec]
 * frame) may need several characteristic writes. [encodeChunks] splits a
 * message into MTU-sized pieces prefixed once with a 4-byte total length;
 * [Reassembler] is the per-connection state machine that glues them back
 * together on the receiving side.
 */
object MessageFramer {

    fun encodeChunks(message: ByteArray, chunkSize: Int = ProtocolConstants.MTU_CHUNK_BYTES): List<ByteArray> {
        require(chunkSize > ProtocolConstants.LENGTH_PREFIX_BYTES) { "chunkSize must exceed the length-prefix size" }

        val header = ByteArray(ProtocolConstants.LENGTH_PREFIX_BYTES).also { header ->
            header[0] = (message.size ushr 24).toByte()
            header[1] = (message.size ushr 16).toByte()
            header[2] = (message.size ushr 8).toByte()
            header[3] = message.size.toByte()
        }

        val chunks = mutableListOf<ByteArray>()
        val firstChunkCapacity = chunkSize - ProtocolConstants.LENGTH_PREFIX_BYTES
        var offset = 0
        var isFirst = true

        while (isFirst || offset < message.size) {
            val capacity = if (isFirst) firstChunkCapacity else chunkSize
            val end = (offset + capacity).coerceAtMost(message.size)
            val slice = message.copyOfRange(offset, end)
            chunks += if (isFirst) header + slice else slice
            offset = end
            isFirst = false
        }
        return chunks
    }

    /** Stateful, one instance per live connection. Not thread-safe — feed from a single collector. */
    class Reassembler(private val maxMessageBytes: Int = ProtocolConstants.MAX_MESSAGE_BYTES) {
        private var expectedLength = -1
        private var buffer = ByteArrayOutputStream()

        /** Returns the complete message once all chunks have arrived, or null if more chunks are still expected. */
        fun feed(chunk: ByteArray): ByteArray? {
            if (expectedLength < 0) {
                require(chunk.size >= ProtocolConstants.LENGTH_PREFIX_BYTES) {
                    "first chunk of a message must contain the length prefix"
                }
                expectedLength = ((chunk[0].toInt() and 0xFF) shl 24) or
                    ((chunk[1].toInt() and 0xFF) shl 16) or
                    ((chunk[2].toInt() and 0xFF) shl 8) or
                    (chunk[3].toInt() and 0xFF)
                require(expectedLength in 0..maxMessageBytes) {
                    "declared message length $expectedLength is out of bounds"
                }
                buffer = ByteArrayOutputStream(expectedLength)
                buffer.write(chunk, ProtocolConstants.LENGTH_PREFIX_BYTES, chunk.size - ProtocolConstants.LENGTH_PREFIX_BYTES)
            } else {
                buffer.write(chunk)
            }

            return if (buffer.size() >= expectedLength) {
                val complete = buffer.toByteArray()
                reset()
                complete
            } else {
                null
            }
        }

        fun reset() {
            expectedLength = -1
            buffer = ByteArrayOutputStream()
        }
    }
}
