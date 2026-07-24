package com.crowdmesh.mesh

import java.nio.ByteBuffer

/**
 * Encodes a compact best-effort advertising hint (device id prefix + own
 * record version). Purely a scan-time optimization hint — the authoritative
 * exchange always happens post-connection via the HELLO/DIGEST handshake, so
 * this never needs to be exact or collision-free.
 */
object IdentityPayloadEncoder {
    private const val ID_PREFIX_BYTES = 8

    /** Fixed total size of [encode]'s output — used by [com.crowdmesh.mesh.ble.BleScanner] to build its manufacturer-data filter mask. */
    const val PAYLOAD_BYTES = ID_PREFIX_BYTES + Int.SIZE_BYTES

    fun encode(deviceId: String, version: Long): ByteArray {
        val idBytes = deviceId.toByteArray(Charsets.UTF_8).copyOf(ID_PREFIX_BYTES)
        val versionBytes = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(version.toInt()).array()
        return idBytes + versionBytes
    }
}
