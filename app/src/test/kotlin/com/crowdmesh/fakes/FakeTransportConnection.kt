package com.crowdmesh.fakes

import com.crowdmesh.domain.model.TransportKind
import com.crowdmesh.mesh.transport.TransportConnection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** An in-memory, in-process "radio" so [com.crowdmesh.mesh.sync.SyncManager] can be tested without any real transport. */
class FakeTransportConnection(
    override val remoteHandle: String,
    private val outgoing: Channel<ByteArray>,
    private val incoming: Channel<ByteArray>,
) : TransportConnection {
    override val kind: TransportKind = TransportKind.BLE
    override val incomingFrames: Flow<ByteArray> = incoming.receiveAsFlow()

    override suspend fun send(message: ByteArray): Boolean {
        outgoing.send(message)
        return true
    }

    override fun close() {
        outgoing.close()
    }

    companion object {
        /** Two ends of the same wire: whatever A sends, B receives, and vice versa. */
        fun connectedPair(handleA: String = "peer-a", handleB: String = "peer-b"): Pair<FakeTransportConnection, FakeTransportConnection> {
            val aToB = Channel<ByteArray>(Channel.UNLIMITED)
            val bToA = Channel<ByteArray>(Channel.UNLIMITED)
            val connectionA = FakeTransportConnection(handleB, outgoing = aToB, incoming = bToA)
            val connectionB = FakeTransportConnection(handleA, outgoing = bToA, incoming = aToB)
            return connectionA to connectionB
        }
    }
}
