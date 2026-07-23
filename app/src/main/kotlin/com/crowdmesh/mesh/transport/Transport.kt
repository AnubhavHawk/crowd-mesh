package com.crowdmesh.mesh.transport

import com.crowdmesh.domain.model.TransportKind
import com.crowdmesh.mesh.discovery.DiscoveredPeer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/** A live, bidirectional byte-stream to one peer, regardless of which radio carries it. */
interface TransportConnection {
    val remoteHandle: String
    val kind: TransportKind
    val incomingFrames: Flow<ByteArray>
    suspend fun send(message: ByteArray): Boolean
    fun close()
}

/**
 * One radio's worth of mesh capability: discovery, advertising, and
 * connection setup. [com.crowdmesh.mesh.transport.TransportManager] picks
 * which implementation(s) to run; [com.crowdmesh.mesh.sync.SyncManager]
 * only ever talks to the [TransportConnection] abstraction, never to a
 * specific radio API.
 */
interface Transport {
    val kind: TransportKind

    fun isAvailable(): Boolean

    fun startAdvertising(identityPayload: ByteArray)

    fun stopAdvertising()

    fun discoverPeers(dutyCycle: MeshDutyCycle): Flow<DiscoveredPeer>

    /** Actively opens a connection to a peer found via [discoverPeers] (we act as the initiator/client). */
    suspend fun openConnection(peer: DiscoveredPeer): TransportConnection?

    /** Connections a remote peer initiated to us (we act as the server/responder). */
    val incomingConnections: SharedFlow<TransportConnection>
}
