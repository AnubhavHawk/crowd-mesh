package com.crowdmesh.domain.model

/**
 * The unit of exchange in the gossip protocol: a [PresenceRecord] wrapped
 * with the bookkeeping needed to dedupe, expire, and bound flooding across
 * the mesh. This is a transport-time concept — once merged into local
 * storage only [record] survives; [messageId], [ttlExpiresAt] and
 * [hopCount] are not persisted as history.
 */
data class GossipMessage(
    /** Unique per version-broadcast (regenerated every time [record.version] changes). */
    val messageId: String,
    val record: PresenceRecord,
    /** Absolute epoch-millis after which this record is considered stale and is dropped. */
    val ttlExpiresAt: Long,
    /** Number of peer-to-peer relays this message has already been through. */
    val hopCount: Int,
) {
    fun isExpired(nowMillis: Long): Boolean = nowMillis >= ttlExpiresAt

    fun canRelayFurther(maxHops: Int): Boolean = hopCount < maxHops

    fun relayed(): GossipMessage = copy(hopCount = hopCount + 1)
}
