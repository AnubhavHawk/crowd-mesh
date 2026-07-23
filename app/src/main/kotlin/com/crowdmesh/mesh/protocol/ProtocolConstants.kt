package com.crowdmesh.mesh.protocol

object ProtocolConstants {
    const val PROTOCOL_VERSION: Int = 1

    /**
     * Conservative logical-message chunk size, safely under a negotiated
     * BLE ATT MTU of 217 bytes (`217 - 3 header bytes = 214`), leaving room
     * for the 4-byte length prefix [mesh.ble.MessageFramer] adds per chunk.
     */
    const val MTU_CHUNK_BYTES: Int = 185

    /** Bytes used by [mesh.ble.MessageFramer] to prefix each chunk with the total message length. */
    const val LENGTH_PREFIX_BYTES: Int = 4

    /** Upper bound on a single reassembled logical message, to bound memory if a peer misbehaves. */
    const val MAX_MESSAGE_BYTES: Int = 64 * 1024
}
