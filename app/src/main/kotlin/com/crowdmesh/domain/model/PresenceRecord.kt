package com.crowdmesh.domain.model

/**
 * The single presence fact a user owns. Every update *replaces* the previous
 * one for the same [userId] — there is never more than one row per user and
 * no history is retained anywhere in the system.
 */
data class PresenceRecord(
    val userId: String,
    val geohash: String,
    val timestamp: Long,
    val version: Long,
)
