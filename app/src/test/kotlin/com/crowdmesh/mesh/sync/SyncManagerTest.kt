package com.crowdmesh.mesh.sync

import com.crowdmesh.data.repository.PresenceRepositoryImpl
import com.crowdmesh.domain.model.PresenceRecord
import com.crowdmesh.fakes.FakeGossipLedgerRepository
import com.crowdmesh.fakes.FakeIdentityProvider
import com.crowdmesh.fakes.FakePresenceRecordDao
import com.crowdmesh.fakes.FakeTimeProvider
import com.crowdmesh.fakes.FakeTransportConnection
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncManagerTest {

    private fun buildPeer(deviceId: String): Triple<PresenceRepositoryImpl, SyncManager, FakeTimeProvider> {
        val timeProvider = FakeTimeProvider()
        val repository = PresenceRepositoryImpl(FakePresenceRecordDao(), FakeIdentityProvider(deviceId), timeProvider)
        val messageStore = MessageStore(FakeGossipLedgerRepository(), timeProvider)
        val syncManager = SyncManager(repository, FakeIdentityProvider(deviceId), messageStore)
        return Triple(repository, syncManager, timeProvider)
    }

    @Test
    fun `a record known only to A propagates to B after one sync`() = runTest {
        val (repoA, syncA, timeA) = buildPeer("device-a")
        val (repoB, syncB, _) = buildPeer("device-b")

        repoA.upsertOwnRecord("device-a", "u4pruyd0", timeA.nowMillis())

        val (connA, connB) = FakeTransportConnection.connectedPair()

        val jobA = launch { syncA.syncOverConnection(connA) }
        val jobB = launch { syncB.syncOverConnection(connB) }
        jobA.join()
        jobB.join()

        val replicated = repoB.getRecord("device-a")
        assertEquals("u4pruyd0", replicated?.geohash)
        assertEquals(1L, replicated?.version)
    }

    @Test
    fun `sync is bidirectional in a single encounter`() = runTest {
        val (repoA, syncA, timeA) = buildPeer("device-a")
        val (repoB, syncB, timeB) = buildPeer("device-b")

        repoA.upsertOwnRecord("device-a", "u4pruyd0", timeA.nowMillis())
        repoB.upsertOwnRecord("device-b", "u4pruyd9", timeB.nowMillis())

        val (connA, connB) = FakeTransportConnection.connectedPair()
        val jobA = launch { syncA.syncOverConnection(connA) }
        val jobB = launch { syncB.syncOverConnection(connB) }
        jobA.join()
        jobB.join()

        assertEquals("u4pruyd9", repoA.getRecord("device-b")?.geohash)
        assertEquals("u4pruyd0", repoB.getRecord("device-a")?.geohash)
    }

    @Test
    fun `a peer that already has the newer version is not sent a stale one`() = runTest {
        val (repoA, syncA, timeA) = buildPeer("device-a")
        val (repoB, syncB, _) = buildPeer("device-b")

        // B already knows a newer version of device-a's record than A itself is about to send.
        repoB.mergeRemoteRecord(
            PresenceRecord("device-a", "u4pruyd9", timeA.nowMillis(), version = 5),
            ttlExpiresAtMillis = timeA.nowMillis() + 60_000,
        )
        repoA.upsertOwnRecord("device-a", "u4pruyd0", timeA.nowMillis()) // version 1, older

        val (connA, connB) = FakeTransportConnection.connectedPair()
        val jobA = launch { syncA.syncOverConnection(connA) }
        val jobB = launch { syncB.syncOverConnection(connB) }
        jobA.join()
        jobB.join()

        // B's newer copy must not be clobbered by A's stale version 1.
        assertEquals(5L, repoB.getRecord("device-a")?.version)
        // And A should have learned B's newer copy of its own record back.
        assertEquals(5L, repoA.getRecord("device-a")?.version)
    }

    @Test
    fun `neither side sends anything when both start empty`() = runTest {
        val (repoA, syncA, _) = buildPeer("device-a")
        val (repoB, syncB, _) = buildPeer("device-b")

        val (connA, connB) = FakeTransportConnection.connectedPair()
        val jobA = launch { syncA.syncOverConnection(connA) }
        val jobB = launch { syncB.syncOverConnection(connB) }
        jobA.join()
        jobB.join()

        assertNull(repoA.getRecord("device-b"))
        assertNull(repoB.getRecord("device-a"))
    }
}
