package com.crowdmesh.domain.heatmap

import com.crowdmesh.domain.geohash.GeohashEncoder
import com.crowdmesh.domain.model.DensityLevel
import com.crowdmesh.domain.model.HeatmapCell
import com.crowdmesh.domain.model.PresenceRecord
import kotlin.math.max
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregates currently-known [PresenceRecord]s into [HeatmapCell]s, entirely
 * on-device. This is pure, deterministic, stateless computation — it is the
 * whole "heatmap" feature; nothing about it is synchronized across the mesh.
 */
@Singleton
class HeatmapAggregator @Inject constructor() {

    fun aggregate(
        records: List<PresenceRecord>,
        nowMillis: Long,
        cellPrecision: Int = DEFAULT_CELL_PRECISION,
        confidenceDecayWindowMillis: Long = DEFAULT_CONFIDENCE_DECAY_WINDOW_MILLIS,
    ): List<HeatmapCell> {
        if (records.isEmpty()) return emptyList()

        return records
            .groupBy { record -> GeohashEncoder.prefix(record.geohash, cellPrecision.coerceAtMost(record.geohash.length)) }
            .map { (cellGeohash, cellRecords) ->
                val distinctUsers = cellRecords.map { it.userId }.distinct().size
                val latestTimestamp = cellRecords.maxOf { it.timestamp }
                val confidence = confidenceFor(latestTimestamp, nowMillis, confidenceDecayWindowMillis)
                HeatmapCell(
                    geohash = cellGeohash,
                    userCount = distinctUsers,
                    latestTimestampMillis = latestTimestamp,
                    confidence = confidence,
                    level = levelFor(distinctUsers, confidence),
                )
            }
            .sortedByDescending { it.userCount }
    }

    /** Linear decay: 1.0 when just updated, 0.0 once [confidenceDecayWindowMillis] has elapsed. */
    internal fun confidenceFor(
        latestTimestampMillis: Long,
        nowMillis: Long,
        confidenceDecayWindowMillis: Long,
    ): Double {
        val ageMillis = (nowMillis - latestTimestampMillis).coerceAtLeast(0)
        if (confidenceDecayWindowMillis <= 0) return 0.0
        val remaining = 1.0 - (ageMillis.toDouble() / confidenceDecayWindowMillis.toDouble())
        return remaining.coerceIn(0.0, 1.0)
    }

    internal fun levelFor(userCount: Int, confidence: Double): DensityLevel {
        val weightedDensity = max(userCount * confidence, 0.0)
        return when {
            weightedDensity >= RED_THRESHOLD -> DensityLevel.RED
            weightedDensity >= ORANGE_THRESHOLD -> DensityLevel.ORANGE
            weightedDensity >= YELLOW_THRESHOLD -> DensityLevel.YELLOW
            else -> DensityLevel.GREEN
        }
    }

    companion object {
        /** Geohash prefix length used for heatmap cells (~0.6km x 1.2km at the equator). */
        const val DEFAULT_CELL_PRECISION = 6
        const val DEFAULT_CONFIDENCE_DECAY_WINDOW_MILLIS = 30 * 60 * 1000L

        const val YELLOW_THRESHOLD = 3.0
        const val ORANGE_THRESHOLD = 8.0
        const val RED_THRESHOLD = 20.0
    }
}
