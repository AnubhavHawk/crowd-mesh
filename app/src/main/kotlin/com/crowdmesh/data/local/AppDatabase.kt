package com.crowdmesh.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.crowdmesh.data.local.dao.KnownPeerDao
import com.crowdmesh.data.local.dao.PresenceRecordDao
import com.crowdmesh.data.local.dao.ReceivedMessageIdDao
import com.crowdmesh.data.local.entity.KnownPeerEntity
import com.crowdmesh.data.local.entity.PresenceRecordEntity
import com.crowdmesh.data.local.entity.ReceivedMessageIdEntity

/**
 * The entire local store. Deliberately three tables and nothing else:
 * current presence facts, known peers, and the gossip dedup ledger.
 */
@Database(
    entities = [
        PresenceRecordEntity::class,
        KnownPeerEntity::class,
        ReceivedMessageIdEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun presenceRecordDao(): PresenceRecordDao
    abstract fun knownPeerDao(): KnownPeerDao
    abstract fun receivedMessageIdDao(): ReceivedMessageIdDao

    companion object {
        const val DATABASE_NAME = "crowdmesh.db"
    }
}
