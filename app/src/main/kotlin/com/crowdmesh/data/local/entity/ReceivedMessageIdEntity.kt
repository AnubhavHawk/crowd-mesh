package com.crowdmesh.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Durable dedup ledger: gossip message IDs already seen, so relays/merges never repeat. */
@Entity(tableName = "received_message_ids")
data class ReceivedMessageIdEntity(
    @PrimaryKey val messageId: String,
    val receivedAt: Long,
)
