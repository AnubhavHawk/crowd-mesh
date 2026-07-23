package com.crowdmesh.domain.model

enum class MeshActivity { IDLE, SCANNING, SYNCING }

data class MeshStatus(
    val activity: MeshActivity = MeshActivity.IDLE,
    val nearbyPeerCount: Int = 0,
    val syncingPeerCount: Int = 0,
    val activeTransports: Set<TransportKind> = emptySet(),
)
