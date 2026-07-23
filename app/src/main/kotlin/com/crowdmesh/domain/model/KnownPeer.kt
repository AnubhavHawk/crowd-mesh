package com.crowdmesh.domain.model

enum class TransportKind { BLE, WIFI_AWARE, WIFI_DIRECT }

/** A device the mesh has previously discovered or synced with. */
data class KnownPeer(
    val deviceId: String,
    val transportKind: TransportKind,
    val lastSeenAt: Long,
    val lastSyncedAt: Long?,
    val lastRssi: Int? = null,
)
