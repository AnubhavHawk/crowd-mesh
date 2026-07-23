package com.crowdmesh.mesh

import com.crowdmesh.di.ApplicationScope
import com.crowdmesh.domain.model.MeshActivity
import com.crowdmesh.domain.model.MeshStatus
import com.crowdmesh.domain.repository.IdentityProvider
import com.crowdmesh.domain.repository.MeshController
import com.crowdmesh.domain.repository.PresenceRepository
import com.crowdmesh.mesh.discovery.PeerDiscoveryManager
import com.crowdmesh.mesh.peer.ConnectionManager
import com.crowdmesh.mesh.sync.SyncManager
import com.crowdmesh.mesh.transport.MeshDutyCycle
import com.crowdmesh.mesh.transport.TransportConnection
import com.crowdmesh.mesh.transport.TransportManager
import com.crowdmesh.util.Logger
import com.crowdmesh.util.TimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The whole mesh subsystem's public face: wires transport discovery,
 * connection management, and gossip sync together, and is completely
 * UI-independent — [com.crowdmesh.presentation] only ever sees this through
 * the [MeshController] port. Lives for the process lifetime once [start] is
 * called (from [com.crowdmesh.CrowdMeshApp] or the first Home screen visit).
 */
@Singleton
class MeshEngine @Inject constructor(
    private val peerDiscoveryManager: PeerDiscoveryManager,
    private val connectionManager: ConnectionManager,
    private val transportManager: TransportManager,
    private val syncManager: SyncManager,
    private val identityProvider: IdentityProvider,
    private val presenceRepository: PresenceRepository,
    private val timeProvider: TimeProvider,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : MeshController {

    private val _status = MutableStateFlow(MeshStatus())
    override val status: StateFlow<MeshStatus> = _status.asStateFlow()

    private var started = false
    private var discoveryJob: Job? = null
    private val activeSyncCount = AtomicInteger(0)
    private val recentSightings = ConcurrentHashMap<String, Long>()

    init {
        // Always answer an inbound connection regardless of our own duty cycle —
        // the peer already spent its own radio budget reaching out to us.
        applicationScope.launch {
            transportManager.incomingConnections.collect { connection ->
                launch {
                    try {
                        trackedSync(connection)
                    } finally {
                        connection.close()
                    }
                }
            }
        }
    }

    override fun start() {
        if (started) return
        started = true
        _status.update { it.copy(activeTransports = transportManager.availableTransports.map { t -> t.kind }.toSet()) }
        applicationScope.launch { advertiseCurrentRecord() }
        setDutyCycle(MeshDutyCycle.FOREGROUND_IDLE)
    }

    override fun stop() {
        started = false
        discoveryJob?.cancel()
        discoveryJob = null
        peerDiscoveryManager.stopAdvertising()
        recentSightings.clear()
        _status.update { it.copy(activity = MeshActivity.IDLE, nearbyPeerCount = 0, syncingPeerCount = 0) }
    }

    override fun notifyLocalRecordChanged() {
        applicationScope.launch { advertiseCurrentRecord() }
        if (started) setDutyCycle(MeshDutyCycle.ACTIVE)
    }

    private fun setDutyCycle(dutyCycle: MeshDutyCycle) {
        discoveryJob?.cancel()
        _status.update { it.copy(activity = MeshActivity.SCANNING) }
        discoveryJob = applicationScope.launch {
            peerDiscoveryManager.discover(dutyCycle).collect { peer ->
                recordSighting(peer.connectionHandle, peer.timestampMillis)
                connectionManager.connectIfWorthwhile(peer) { connection -> trackedSync(connection) }
            }
        }
    }

    private suspend fun trackedSync(connection: TransportConnection) {
        val active = activeSyncCount.incrementAndGet()
        _status.update { it.copy(activity = MeshActivity.SYNCING, syncingPeerCount = active) }
        try {
            syncManager.syncOverConnection(connection)
        } catch (e: Exception) {
            Logger.w(TAG, "sync with ${connection.remoteHandle} failed", e)
        } finally {
            val remaining = activeSyncCount.decrementAndGet()
            _status.update {
                it.copy(
                    activity = if (remaining > 0) MeshActivity.SYNCING else MeshActivity.SCANNING,
                    syncingPeerCount = remaining,
                )
            }
        }
    }

    private fun recordSighting(connectionHandle: String, seenAtMillis: Long) {
        recentSightings[connectionHandle] = seenAtMillis
        val cutoff = timeProvider.nowMillis() - NEARBY_WINDOW_MILLIS
        recentSightings.entries.removeIf { it.value < cutoff }
        _status.update { it.copy(nearbyPeerCount = recentSightings.size) }
    }

    private suspend fun advertiseCurrentRecord() {
        val deviceId = identityProvider.getOrCreateDeviceId()
        val ownVersion = presenceRepository.getRecord(deviceId)?.version ?: 0L
        peerDiscoveryManager.startAdvertising(IdentityPayloadEncoder.encode(deviceId, ownVersion))
    }

    private companion object {
        const val TAG = "MeshEngine"
        const val NEARBY_WINDOW_MILLIS = 2 * 60 * 1000L
    }
}
