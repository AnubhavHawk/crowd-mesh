package com.crowdmesh.mesh.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MessageFramerTest {

    @Test
    fun `message smaller than one chunk round-trips in a single chunk`() {
        val message = "hello".toByteArray()
        val chunks = MessageFramer.encodeChunks(message, chunkSize = 64)
        assertEquals(1, chunks.size)

        val reassembler = MessageFramer.Reassembler()
        val result = reassembler.feed(chunks[0])
        assertArrayEquals(message, result)
    }

    @Test
    fun `message spanning multiple chunks reassembles to the original bytes`() {
        val message = Random(seed = 42).nextBytes(1000)
        val chunks = MessageFramer.encodeChunks(message, chunkSize = 64)
        assertTrue(chunks.size > 1)

        val reassembler = MessageFramer.Reassembler()
        var result: ByteArray? = null
        for (chunk in chunks) {
            val maybeComplete = reassembler.feed(chunk)
            if (maybeComplete != null) {
                assertNull("should only complete on the final chunk", result)
                result = maybeComplete
            }
        }
        assertArrayEquals(message, result)
    }

    @Test
    fun `empty message round-trips`() {
        val chunks = MessageFramer.encodeChunks(ByteArray(0), chunkSize = 64)
        val reassembler = MessageFramer.Reassembler()
        val result = reassembler.feed(chunks.single())
        assertArrayEquals(ByteArray(0), result)
    }

    @Test
    fun `reassembler can be reused for a second message after completion`() {
        val reassembler = MessageFramer.Reassembler()
        val first = "first".toByteArray()
        val second = "second-message".toByteArray()

        MessageFramer.encodeChunks(first, chunkSize = 64).forEach { reassembler.feed(it) }
        val result = MessageFramer.encodeChunks(second, chunkSize = 64).map { reassembler.feed(it) }.last()

        assertArrayEquals(second, result)
    }
}
