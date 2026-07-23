package com.crowdmesh.mesh.sync

import com.crowdmesh.domain.GossipPolicy
import com.crowdmesh.domain.model.GossipMessage
import com.crowdmesh.domain.model.PresenceRecord
import com.crowdmesh.fakes.FakeGossipLedgerRepository
import com.crowdmesh.fakes.FakeTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MessageStoreTest {

    private lateinit var ledger: FakeGossipLedgerRepository
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var messageStore: MessageStore

    @Before
    fun setUp() {
        ledger = FakeGossipLedgerRepository()
        timeProvider = FakeTimeProvider()
        messageStore = MessageStore(ledger, timeProvider)
    }

    @Test
    fun `a fresh message should be processed`() = runTest {
        assertTrue(messageStore.shouldProcess(message()))
    }

    @Test
    fun `an already-processed message is not processed again`() = runTest {
        val message = message()
        messageStore.markProcessed(message)
        assertFalse(messageStore.shouldProcess(message))
    }

    @Test
    fun `dedup survives even if the in-memory cache is bypassed (durable ledger)`() = runTest {
        val message = message()
        ledger.markSeen(message.messageId, timeProvider.nowMillis())
        assertFalse(messageStore.shouldProcess(message))
    }

    @Test
    fun `an expired message should not be processed`() = runTest {
        timeProvider.currentMillis = 10_000L
        val message = message(ttlExpiresAt = 5_000L)
        assertFalse(messageStore.shouldProcess(message))
    }

    @Test
    fun `a message beyond the max hop count should not be processed`() = runTest {
        val message = message(hopCount = GossipPolicy.MAX_HOPS)
        assertFalse(messageStore.shouldProcess(message))
    }

    @Test
    fun `a message just under the max hop count is processed`() = runTest {
        val message = message(hopCount = GossipPolicy.MAX_HOPS - 1)
        assertTrue(messageStore.shouldProcess(message))
    }

    private fun message(
        messageId: String = "user-a:1",
        ttlExpiresAt: Long = timeProvider.nowMillis() + 60_000L,
        hopCount: Int = 0,
    ) = GossipMessage(
        messageId = messageId,
        record = PresenceRecord(userId = "user-a", geohash = "u4pruyd0", timestamp = timeProvider.nowMillis(), version = 1L),
        ttlExpiresAt = ttlExpiresAt,
        hopCount = hopCount,
    )
}
