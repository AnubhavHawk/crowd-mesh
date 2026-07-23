package com.crowdmesh.domain.model

enum class DensityLevel { GREEN, YELLOW, ORANGE, RED }

/**
 * A single aggregated geohash cell, computed entirely on-device from
 * currently-known [PresenceRecord]s. Never synchronized across the mesh —
 * only the underlying [PresenceRecord]s travel; every device computes its
 * own heatmap locally.
 */
data class HeatmapCell(
    val geohash: String,
    val userCount: Int,
    val latestTimestampMillis: Long,
    /** 1.0 = just updated, decays toward 0 as the newest record in the cell ages. */
    val confidence: Double,
    val level: DensityLevel,
)
