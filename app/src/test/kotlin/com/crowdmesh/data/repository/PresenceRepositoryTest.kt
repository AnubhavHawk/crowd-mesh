package com.crowdmesh.data.repository

import app.cash.turbine.test
import com.crowdmesh.domain.model.PresenceRecord
import com.crowdmesh.fakes.FakeIdentityProvider
import com.crowdmesh.fakes.FakePresenceRecordDao
import com.crowdmesh.fakes.FakeTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PresenceRepositoryTest {

    private lateinit var dao: FakePresenceRecordDao
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var repository: PresenceRepositoryImpl

    @Before
    fun setUp() {
        dao = FakePresenceRecordDao()
        timeProvider = FakeTimeProvider()
        repository = PresenceRepositoryImpl(dao, FakeIdentityProvider("own-device"), timeProvider)
    }

    @Test
    fun `first update starts at version 1`() = runTest {
        val record = repository.upsertOwnRecord("own-device", "u4pruyd0", timeProvider.nowMillis())
        assertEquals(1L, record.version)
    }

    @Test
    fun `subsequent updates replace the single row and increment version`() = runTest {
        repository.upsertOwnRecord("own-device", "u4pruyd0", timeProvider.nowMillis())
        val second = repository.upsertOwnRecord("own-device", "u4pruyd1", timeProvider.nowMillis() + 1)

        assertEquals(2L, second.version)
        assertEquals(second, repository.getRecord("own-device"))
    }

    @Test
    fun `merging a newer remote record is applied`() = runTest {
        val incoming = PresenceRecord("peer-1", "u4pruyd0", timeProvider.nowMillis(), version = 1)
        val applied = repository.mergeRemoteRecord(incoming, ttlExpiresAtMillis = timeProvider.nowMillis() + 60_000)

        assertTrue(applied)
        assertEquals(incoming, repository.getRecord("peer-1"))
    }

    @Test
    fun `merging a stale remote record is ignored`() = runTest {
        val newer = PresenceRecord("peer-1", "u4pruyd0", timeProvider.nowMillis(), version = 5)
        repository.mergeRemoteRecord(newer, timeProvider.nowMillis() + 60_000)

        val stale = PresenceRecord("peer-1", "u4pruyd9", timeProvider.nowMillis() - 1000, version = 4)
        val applied = repository.mergeRemoteRecord(stale, timeProvider.nowMillis() + 60_000)

        assertFalse(applied)
        assertEquals(newer, repository.getRecord("peer-1"))
    }

    @Test
    fun `observeAllRecords excludes expired records`() = runTest {
        repository.mergeRemoteRecord(
            PresenceRecord("peer-1", "u4pruyd0", timeProvider.nowMillis(), 1),
            ttlExpiresAtMillis = timeProvider.nowMillis() - 1, // already expired
        )
        repository.mergeRemoteRecord(
            PresenceRecord("peer-2", "u4pruyd0", timeProvider.nowMillis(), 1),
            ttlExpiresAtMillis = timeProvider.nowMillis() + 60_000,
        )

        repository.observeAllRecords().test {
            val records = awaitItem()
            assertEquals(1, records.size)
            assertEquals("peer-2", records.first().userId)
        }
    }

    @Test
    fun `observeOwnRecord is null until the first update`() = runTest {
        repository.observeOwnRecord().test {
            assertNull(awaitItem())
        }
    }

    @Test
    fun `recordsForDigest returns the most recently updated records first`() = runTest {
        repository.mergeRemoteRecord(PresenceRecord("a", "u4pruyd0", 100, 1), 999_999_999_999)
        repository.mergeRemoteRecord(PresenceRecord("b", "u4pruyd0", 300, 1), 999_999_999_999)
        repository.mergeRemoteRecord(PresenceRecord("c", "u4pruyd0", 200, 1), 999_999_999_999)

        val digest = repository.recordsForDigest(limit = 2)

        assertEquals(2, digest.size)
        assertEquals("b", digest[0].userId)
        assertEquals("c", digest[1].userId)
    }

    @Test
    fun `pruneExpired removes only expired records`() = runTest {
        repository.mergeRemoteRecord(PresenceRecord("expired", "u4pruyd0", 0, 1), ttlExpiresAtMillis = timeProvider.nowMillis() - 1)
        repository.mergeRemoteRecord(PresenceRecord("alive", "u4pruyd0", 0, 1), ttlExpiresAtMillis = timeProvider.nowMillis() + 60_000)

        val removed = repository.pruneExpired(timeProvider.nowMillis())

        assertEquals(1, removed)
        assertNull(repository.getRecord("expired"))
        assertTrue(repository.getRecord("alive") != null)
    }
}
