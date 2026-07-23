package com.crowdmesh.domain.repository

import com.crowdmesh.domain.model.KnownPeer
import kotlinx.coroutines.flow.Flow

/** Bookkeeping of devices the mesh has discovered or synced with. */
interface PeerRepository {

    fun observeKnownPeers(): Flow<List<KnownPeer>>

    suspend fun upsertPeer(peer: KnownPeer)

    suspend fun getPeer(deviceId: String): KnownPeer?

    suspend fun pruneStale(nowMillis: Long, staleAfterMillis: Long): Int
}
