package com.crowdmesh.data.repository

import com.crowdmesh.data.local.dao.KnownPeerDao
import com.crowdmesh.domain.model.KnownPeer
import com.crowdmesh.domain.repository.PeerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeerRepositoryImpl @Inject constructor(
    private val dao: KnownPeerDao,
) : PeerRepository {

    override fun observeKnownPeers(): Flow<List<KnownPeer>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun upsertPeer(peer: KnownPeer) = dao.upsert(peer.toEntity())

    override suspend fun getPeer(deviceId: String): KnownPeer? = dao.get(deviceId)?.toDomain()

    override suspend fun pruneStale(nowMillis: Long, staleAfterMillis: Long): Int =
        dao.deleteStale(nowMillis - staleAfterMillis)
}
