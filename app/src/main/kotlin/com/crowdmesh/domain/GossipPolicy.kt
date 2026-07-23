package com.crowdmesh.domain

/**
 * Tunable knobs shared by the data layer (record TTL on write) and the mesh
 * layer (relay/flood bounds). Framework-free so both sides can depend on it
 * without creating a data<->mesh coupling.
 */
object GossipPolicy {
    /** How long a presence fact stays valid after it was captured. Crowds move; keep this short. */
    const val RECORD_TTL_MILLIS: Long = 30 * 60 * 1000L

    /** Hard cap on epidemic-relay depth so gossip can't loop/flood forever. */
    const val MAX_HOPS: Int = 6

    /** Upper bound on how many (userId, version) pairs a single digest exchange carries. */
    const val DIGEST_ENTRY_LIMIT: Int = 500
}
