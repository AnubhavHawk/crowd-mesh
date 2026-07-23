package com.crowdmesh.mesh.discovery

import com.crowdmesh.domain.model.TransportKind

/** A peer seen by any transport's discovery mechanism, before a connection is attempted. */
data class DiscoveredPeer(
    /** Opaque transport-specific handle: a BLE MAC address, a Wi-Fi Aware/Direct endpoint id, etc. */
    val connectionHandle: String,
    val transportKind: TransportKind,
    val rssi: Int?,
    val timestampMillis: Long,
)
