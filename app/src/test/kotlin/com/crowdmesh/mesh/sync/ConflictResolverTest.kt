package com.crowdmesh.mesh.sync

import com.crowdmesh.domain.model.PresenceRecord
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictResolverTest {

    @Test
    fun `no local record means remote is always newer`() {
        val remote = record(version = 1, timestamp = 100)
        assertTrue(ConflictResolver.isNewer(null, remote))
    }

    @Test
    fun `higher version wins regardless of timestamp`() {
        val local = record(version = 5, timestamp = 1_000)
        val remote = record(version = 6, timestamp = 1) // much older wall-clock time, but newer version
        assertTrue(ConflictResolver.isNewer(local, remote))
    }

    @Test
    fun `lower version never wins`() {
        val local = record(version = 6, timestamp = 1)
        val remote = record(version = 5, timestamp = 1_000)
        assertFalse(ConflictResolver.isNewer(local, remote))
    }

    @Test
    fun `equal version breaks tie by timestamp`() {
        val local = record(version = 3, timestamp = 100)
        val newerRemote = record(version = 3, timestamp = 200)
        val olderRemote = record(version = 3, timestamp = 50)

        assertTrue(ConflictResolver.isNewer(local, newerRemote))
        assertFalse(ConflictResolver.isNewer(local, olderRemote))
    }

    @Test
    fun `identical record is not considered newer`() {
        val record = record(version = 3, timestamp = 100)
        assertFalse(ConflictResolver.isNewer(record, record.copy()))
    }

    private fun record(version: Long, timestamp: Long, userId: String = "user-1") =
        PresenceRecord(userId = userId, geohash = "u4pruyd0", timestamp = timestamp, version = version)
}
