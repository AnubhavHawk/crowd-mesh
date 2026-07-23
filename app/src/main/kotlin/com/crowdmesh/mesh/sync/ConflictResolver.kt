package com.crowdmesh.mesh.sync

import com.crowdmesh.domain.model.PresenceRecord

/**
 * "Latest version wins" — the sole conflict-resolution rule for presence
 * records. A pure function so it's trivially unit-testable and reusable
 * wherever a version/timestamp comparison is needed (digest diffing here;
 * [com.crowdmesh.data.repository.PresenceRepositoryImpl] re-applies the same
 * rule as the final authoritative gate at persistence time).
 */
object ConflictResolver {
    fun isNewer(local: PresenceRecord?, remote: PresenceRecord): Boolean {
        if (local == null) return true
        if (remote.userId != local.userId) return true
        return remote.version > local.version ||
            (remote.version == local.version && remote.timestamp > local.timestamp)
    }
}
