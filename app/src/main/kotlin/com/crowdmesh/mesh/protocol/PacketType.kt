package com.crowdmesh.mesh.protocol

/** One byte on the wire identifying the payload that follows it. */
enum class PacketType(val id: Byte) {
    HELLO(1),
    DIGEST(2),
    RECORD_REQUEST(3),
    RECORD_BATCH(4),
    BYE(5),
    ;

    companion object {
        fun fromId(id: Byte): PacketType? = entries.firstOrNull { it.id == id }
    }
}
