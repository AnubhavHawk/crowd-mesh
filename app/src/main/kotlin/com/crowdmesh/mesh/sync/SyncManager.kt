package com.crowdmesh.mesh.sync

import com.crowdmesh.data.serialization.DigestDto
import com.crowdmesh.data.serialization.DigestEntryDto
import com.crowdmesh.data.serialization.PacketCodec
import com.crowdmesh.data.serialization.RecordBatchDto
import com.crowdmesh.data.serialization.RecordRequestDto
import com.crowdmesh.data.serialization.HelloDto
import com.crowdmesh.data.serialization.toDomain
import com.crowdmesh.data.serialization.toWireDto
import com.crowdmesh.domain.GossipPolicy
import com.crowdmesh.domain.model.GossipMessage
import com.crowdmesh.domain.repository.IdentityProvider
import com.crowdmesh.domain.repository.PresenceRepository
import com.crowdmesh.mesh.protocol.ProtocolConstants
import com.crowdmesh.mesh.transport.TransportConnection
import com.crowdmesh.util.Logger
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives one full gossip exchange over an already-open [TransportConnection].
 * Both peers run this same function concurrently (one from
 * [com.crowdmesh.mesh.peer.ConnectionManager]'s outbound path, the other
 * from a transport's `incomingConnections`), forming a symmetric
 * HELLO -> DIGEST -> REQUEST -> RECORD_BATCH handshake:
 *
 * ```
 * A                                B
 * |-- HELLO(A) --------------------|
 * |---------------------- HELLO(B) -|
 * |-- DIGEST(A's known versions) --|
 * |------------ DIGEST(B's known) -|
 * |-- REQUEST(userIds A needs) ----|
 * |---------- REQUEST(B needs) ----|
 * |-- RECORD_BATCH(for B's request)|
 * |---- RECORD_BATCH(for A's req) -|
 * ```
 *
 * Only records the other side is actually missing or has an older version
 * of are ever sent — this bounded, need-driven exchange (not blind
 * flooding) is what keeps a single encounter cheap even as the total
 * number of records in the mesh grows toward tens of thousands.
 */
@Singleton
class SyncManager @Inject constructor(
    private val presenceRepository: PresenceRepository,
    private val identityProvider: IdentityProvider,
    private val messageStore: MessageStore,
) {
    suspend fun syncOverConnection(connection: TransportConnection) {
        val myDeviceId = identityProvider.getOrCreateDeviceId()
        Logger.d(TAG, "starting sync with ${connection.remoteHandle} over ${connection.kind}")

        val helloSent = connection.send(
            PacketCodec.encodeHello(HelloDto(myDeviceId, ProtocolConstants.PROTOCOL_VERSION))
        )
        if (!helloSent) return
        val peerHello = receiveOne<PacketCodec.DecodedPacket.Hello>(connection) ?: run {
            Logger.w(TAG, "no HELLO from ${connection.remoteHandle}, aborting sync")
            return
        }
        if (peerHello.value.protocolVersion != ProtocolConstants.PROTOCOL_VERSION) {
            Logger.w(TAG, "protocol version mismatch with ${connection.remoteHandle}, aborting sync")
            return
        }

        val localRecords = presenceRepository.recordsForDigest(GossipPolicy.DIGEST_ENTRY_LIMIT)
        val localByUserId = localRecords.associateBy { it.userId }

        connection.send(
            PacketCodec.encodeDigest(DigestDto(localRecords.map { DigestEntryDto(it.userId, it.version) }))
        )
        val peerDigest = receiveOne<PacketCodec.DecodedPacket.Digest>(connection)?.value ?: run {
            Logger.w(TAG, "no DIGEST from ${connection.remoteHandle}, aborting sync")
            return
        }

        val needFromPeer = peerDigest.entries
            .filter { entry -> entry.version > (localByUserId[entry.userId]?.version ?: -1L) }
            .map { it.userId }

        connection.send(PacketCodec.encodeRecordRequest(RecordRequestDto(needFromPeer)))
        val peerRequest = receiveOne<PacketCodec.DecodedPacket.RecordRequest>(connection)?.value ?: run {
            Logger.w(TAG, "no RECORD_REQUEST from ${connection.remoteHandle}, aborting sync")
            return
        }

        val outgoingBatch = peerRequest.userIds.mapNotNull { userId -> localByUserId[userId] }.map { record ->
            GossipMessage(
                messageId = "${record.userId}:${record.version}",
                record = record,
                ttlExpiresAt = record.timestamp + GossipPolicy.RECORD_TTL_MILLIS,
                hopCount = 0,
            ).toWireDto()
        }
        connection.send(PacketCodec.encodeRecordBatch(RecordBatchDto(outgoingBatch)))

        if (needFromPeer.isNotEmpty()) {
            val batch = receiveOne<PacketCodec.DecodedPacket.RecordBatch>(connection)?.value
            batch?.records?.forEach { wire -> applyIncoming(wire.toDomain()) }
        }

        connection.send(PacketCodec.encodeBye())
        Logger.d(TAG, "sync with ${connection.remoteHandle} complete: sent ${outgoingBatch.size}, requested ${needFromPeer.size}")
    }

    private suspend fun applyIncoming(message: GossipMessage) {
        if (!messageStore.shouldProcess(message)) return
        val existing = presenceRepository.getRecord(message.record.userId)
        if (ConflictResolver.isNewer(existing, message.record)) {
            presenceRepository.mergeRemoteRecord(message.record, message.ttlExpiresAt)
        }
        messageStore.markProcessed(message)
    }

    private suspend inline fun <reified T : PacketCodec.DecodedPacket> receiveOne(
        connection: TransportConnection,
    ): T? = withTimeoutOrNull(RECEIVE_TIMEOUT_MILLIS) {
        connection.incomingFrames
            .map { PacketCodec.decode(it) }
            .filterIsInstance<T>()
            .first()
    }

    private companion object {
        const val TAG = "SyncManager"
        const val RECEIVE_TIMEOUT_MILLIS = 10_000L
    }
}
