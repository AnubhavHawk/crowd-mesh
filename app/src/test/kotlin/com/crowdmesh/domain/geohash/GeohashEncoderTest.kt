package com.crowdmesh.domain.geohash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeohashEncoderTest {

    @Test
    fun `encode produces the well-known reference geohash`() {
        // 57.64911, 10.40744 -> "u4pruydqqvj" is the textbook geohash.org example.
        val hash = GeohashEncoder.encode(57.64911, 10.40744, precision = 11)
        assertEquals("u4pruydqqvj", hash)
    }

    @Test
    fun `encode respects requested precision`() {
        val hash = GeohashEncoder.encode(37.7749, -122.4194, precision = 5)
        assertEquals(5, hash.length)
    }

    @Test
    fun `encode is deterministic`() {
        val a = GeohashEncoder.encode(12.9716, 77.5946, precision = 8)
        val b = GeohashEncoder.encode(12.9716, 77.5946, precision = 8)
        assertEquals(a, b)
    }

    @Test
    fun `nearby points share a coarser prefix`() {
        val a = GeohashEncoder.encode(12.97160, 77.59460, precision = 8)
        val b = GeohashEncoder.encode(12.97162, 77.59462, precision = 8)
        assertEquals(GeohashEncoder.prefix(a, 6), GeohashEncoder.prefix(b, 6))
    }

    @Test
    fun `decodeBounds contains the original point`() {
        val lat = 40.6892
        val lon = -74.0445
        val hash = GeohashEncoder.encode(lat, lon, precision = 9)
        val bounds = GeohashEncoder.decodeBounds(hash)
        assertTrue(lat in bounds.minLat..bounds.maxLat)
        assertTrue(lon in bounds.minLon..bounds.maxLon)
    }

    @Test
    fun `encode rejects out-of-range latitude`() {
        assertThrows(IllegalArgumentException::class.java) {
            GeohashEncoder.encode(95.0, 0.0)
        }
    }

    @Test
    fun `encode rejects invalid precision`() {
        assertThrows(IllegalArgumentException::class.java) {
            GeohashEncoder.encode(0.0, 0.0, precision = 0)
        }
    }
}
