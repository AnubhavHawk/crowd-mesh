package com.crowdmesh.domain.heatmap

import com.crowdmesh.domain.model.DensityLevel
import com.crowdmesh.domain.model.PresenceRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatmapAggregatorTest {

    private val aggregator = HeatmapAggregator()
    private val now = 1_000_000L

    @Test
    fun `records in the same coarse cell are grouped into one HeatmapCell`() {
        val records = listOf(
            record(userId = "a", geohash = "u4pruyd01", timestamp = now),
            record(userId = "b", geohash = "u4pruyd02", timestamp = now),
            record(userId = "c", geohash = "u4pruzzzz", timestamp = now), // different at precision 6
        )

        val cells = aggregator.aggregate(records, now, cellPrecision = 6)

        assertEquals(2, cells.size)
        val bigCell = cells.first { it.userCount == 2 }
        assertEquals("u4pruy", bigCell.geohash)
    }

    @Test
    fun `confidence is 1 immediately after update and decays linearly`() {
        val window = 1_000L
        assertEquals(1.0, aggregator.confidenceFor(now, now, window), 0.0001)
        assertEquals(0.5, aggregator.confidenceFor(now - 500, now, window), 0.0001)
        assertEquals(0.0, aggregator.confidenceFor(now - 2_000, now, window), 0.0001)
    }

    @Test
    fun `density level thresholds map to the documented buckets`() {
        assertEquals(DensityLevel.GREEN, aggregator.levelFor(userCount = 1, confidence = 1.0))
        assertEquals(DensityLevel.YELLOW, aggregator.levelFor(userCount = 3, confidence = 1.0))
        assertEquals(DensityLevel.ORANGE, aggregator.levelFor(userCount = 8, confidence = 1.0))
        assertEquals(DensityLevel.RED, aggregator.levelFor(userCount = 20, confidence = 1.0))
    }

    @Test
    fun `decayed confidence can drop a crowded cell back down a level`() {
        // 20 users, but the newest update in the cell is old enough that confidence has halved.
        val level = aggregator.levelFor(userCount = 20, confidence = 0.3)
        assertTrue(level != DensityLevel.RED)
    }

    @Test
    fun `empty input yields no cells`() {
        assertTrue(aggregator.aggregate(emptyList(), now).isEmpty())
    }

    @Test
    fun `duplicate updates from the same user only count once`() {
        val records = listOf(
            record(userId = "a", geohash = "u4pruyd01", timestamp = now - 10),
            record(userId = "a", geohash = "u4pruyd01", timestamp = now),
        )
        val cells = aggregator.aggregate(records, now, cellPrecision = 6)
        assertEquals(1, cells.size)
        assertEquals(1, cells.first().userCount)
    }

    private fun record(userId: String, geohash: String, timestamp: Long, version: Long = 1L) =
        PresenceRecord(userId = userId, geohash = geohash, timestamp = timestamp, version = version)
}
