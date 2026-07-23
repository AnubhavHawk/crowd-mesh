package com.crowdmesh.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.crowdmesh.data.local.dao.PresenceRecordDao
import com.crowdmesh.data.local.entity.PresenceRecordEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-Room verification of the "one row per user, REPLACE on conflict" contract.
 * Requires a device/emulator (`./gradlew connectedAndroidTest`) — Room's SQLite
 * backing isn't available in a plain JVM unit test without Robolectric, which
 * this project deliberately doesn't depend on (see app/build.gradle.kts notes).
 */
@RunWith(AndroidJUnit4::class)
class PresenceRecordDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: PresenceRecordDao

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.presenceRecordDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun upsertReplacesThePreviousRowForTheSameUser() = runBlocking {
        dao.upsert(PresenceRecordEntity("user-1", "u4pruyd0", timestamp = 100, version = 1, ttlExpiresAt = 999_999_999_999))
        dao.upsert(PresenceRecordEntity("user-1", "u4pruyd1", timestamp = 200, version = 2, ttlExpiresAt = 999_999_999_999))

        val all = dao.observeAll().first()
        assertEquals(1, all.size)
        assertEquals(2L, all.first().version)
        assertEquals("u4pruyd1", all.first().geohash)
    }

    @Test
    fun deleteExpiredOnlyRemovesRecordsPastTheirTtl() = runBlocking {
        dao.upsert(PresenceRecordEntity("expired", "u4pruyd0", 0, 1, ttlExpiresAt = 1_000))
        dao.upsert(PresenceRecordEntity("alive", "u4pruyd0", 0, 1, ttlExpiresAt = 9_999_999_999))

        val removed = dao.deleteExpired(nowMillis = 2_000)

        assertEquals(1, removed)
        assertNull(dao.get("expired"))
        assertEquals("alive", dao.get("alive")?.userId)
    }

    @Test
    fun mostRecentIsOrderedNewestFirstAndRespectsLimit() = runBlocking {
        dao.upsert(PresenceRecordEntity("a", "u4pruyd0", timestamp = 100, version = 1, ttlExpiresAt = 9_999_999_999))
        dao.upsert(PresenceRecordEntity("b", "u4pruyd0", timestamp = 300, version = 1, ttlExpiresAt = 9_999_999_999))
        dao.upsert(PresenceRecordEntity("c", "u4pruyd0", timestamp = 200, version = 1, ttlExpiresAt = 9_999_999_999))

        val topTwo = dao.mostRecent(limit = 2)

        assertEquals(listOf("b", "c"), topTwo.map { it.userId })
    }
}
