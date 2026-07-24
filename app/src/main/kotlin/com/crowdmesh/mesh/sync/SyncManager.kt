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
import kotlinx.coroutines.flow.firstOrNull
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
        val peer = connection.remoteHandle
        Logger.d(TAG, "[SYNC] starting sync with $peer over ${connection.kind}")

        val helloSent = connection.send(
            PacketCodec.encodeHello(HelloDto(myDeviceId, ProtocolConstants.PROTOCOL_VERSION))
        )
        Logger.d(TAG, "[SYNC:HELLO] sent to $peer, success=$helloSent")
        if (!helloSent) {
            Logger.w(TAG, "[SYNC:HELLO] send failed, aborting sync with $peer")
            return
        }
        val peerHello = receiveOne<PacketCodec.DecodedPacket.Hello>(connection) ?: run {
            Logger.w(TAG, "[SYNC:HELLO] no HELLO from $peer within ${RECEIVE_TIMEOUT_MILLIS}ms, aborting sync")
            return
        }
        Logger.d(TAG, "[SYNC:HELLO] received from $peer, deviceId=${peerHello.value.deviceId} protocolVersion=${peerHello.value.protocolVersion}")
        if (peerHello.value.protocolVersion != ProtocolConstants.PROTOCOL_VERSION) {
            Logger.w(TAG, "[SYNC:HELLO] protocol version mismatch with $peer (ours=${ProtocolConstants.PROTOCOL_VERSION}, theirs=${peerHello.value.protocolVersion}), aborting sync")
            return
        }

        val localRecords = presenceRepository.recordsForDigest(GossipPolicy.DIGEST_ENTRY_LIMIT)
        val localByUserId = localRecords.associateBy { it.userId }
        Logger.d(TAG, "[SYNC:DIGEST] sending ${localRecords.size} local record versions to $peer")

        connection.send(
            PacketCodec.encodeDigest(DigestDto(localRecords.map { DigestEntryDto(it.userId, it.version) }))
        )
        val peerDigest = receiveOne<PacketCodec.DecodedPacket.Digest>(connection)?.value ?: run {
            Logger.w(TAG, "[SYNC:DIGEST] no DIGEST from $peer within ${RECEIVE_TIMEOUT_MILLIS}ms, aborting sync")
            return
        }
        Logger.d(TAG, "[SYNC:DIGEST] received ${peerDigest.entries.size} entries from $peer")

        val needFromPeer = peerDigest.entries
            .filter { entry -> entry.version > (localByUserId[entry.userId]?.version ?: -1L) }
            .map { it.userId }
        Logger.d(TAG, "[SYNC:REQUEST] need ${needFromPeer.size} record(s) from $peer: $needFromPeer")

        connection.send(PacketCodec.encodeRecordRequest(RecordRequestDto(needFromPeer)))
        val peerRequest = receiveOne<PacketCodec.DecodedPacket.RecordRequest>(connection)?.value ?: run {
            Logger.w(TAG, "[SYNC:REQUEST] no RECORD_REQUEST from $peer within ${RECEIVE_TIMEOUT_MILLIS}ms, aborting sync")
            return
        }
        Logger.d(TAG, "[SYNC:REQUEST] $peer is requesting ${peerRequest.userIds.size} record(s): ${peerRequest.userIds}")

        val outgoingBatch = peerRequest.userIds.mapNotNull { userId -> localByUserId[userId] }.map { record ->
            GossipMessage(
                messageId = "${record.userId}:${record.version}",
                record = record,
                ttlExpiresAt = record.timestamp + GossipPolicy.RECORD_TTL_MILLIS,
                hopCount = 0,
            ).toWireDto()
        }
        val batchSent = connection.send(PacketCodec.encodeRecordBatch(RecordBatchDto(outgoingBatch)))
        Logger.d(TAG, "[SYNC:BATCH] sent ${outgoingBatch.size} record(s) to $peer, success=$batchSent")

        if (needFromPeer.isNotEmpty()) {
            val batch = receiveOne<PacketCodec.DecodedPacket.RecordBatch>(connection)?.value
            if (batch == null) {
                Logger.w(TAG, "[SYNC:BATCH] no RECORD_BATCH from $peer within ${RECEIVE_TIMEOUT_MILLIS}ms even though we requested ${needFromPeer.size} record(s)")
            } else {
                Logger.d(TAG, "[SYNC:BATCH] received ${batch.records.size} record(s) from $peer")
                batch.records.forEach { wire -> applyIncoming(wire.toDomain()) }
            }
        }

        connection.send(PacketCodec.encodeBye())
        Logger.d(TAG, "[SYNC] complete with $peer: sent ${outgoingBatch.size}, requested ${needFromPeer.size}")
    }

    private suspend fun applyIncoming(message: GossipMessage) {
        if (!messageStore.shouldProcess(message)) {
            Logger.d(TAG, "[SYNC:APPLY] dropping ${message.messageId} (expired, over hop limit, or already seen)")
            return
        }
        val existing = presenceRepository.getRecord(message.record.userId)
        if (ConflictResolver.isNewer(existing, message.record)) {
            presenceRepository.mergeRemoteRecord(message.record, message.ttlExpiresAt)
            Logger.d(TAG, "[SYNC:APPLY] merged ${message.record.userId} v${message.record.version} into local DB")
        } else {
            Logger.d(TAG, "[SYNC:APPLY] ${message.record.userId} v${message.record.version} not newer than local (existing=${existing?.version}), skipped")
        }
        messageStore.markProcessed(message)
    }

    private suspend inline fun <reified T : PacketCodec.DecodedPacket> receiveOne(
        connection: TransportConnection,
    ): T? = withTimeoutOrNull(RECEIVE_TIMEOUT_MILLIS) {
        // firstOrNull, not first(): the underlying connection can legitimately end
        // (peer disconnects, socket closes) before a matching packet ever arrives.
        // first() would throw NoSuchElementException in that case, which isn't a
        // timeout and wasn't being caught anywhere — it was escaping syncOverConnection
        // as an unhandled failure instead of the same graceful "aborting sync" path
        // the timeout branch already has.
        connection.incomingFrames
            .map { PacketCodec.decode(it) }
            .filterIsInstance<T>()
            .firstOrNull()
    }

    private companion object {
        const val TAG = "SyncManager"
        const val RECEIVE_TIMEOUT_MILLIS = 10_000L
    }
}
