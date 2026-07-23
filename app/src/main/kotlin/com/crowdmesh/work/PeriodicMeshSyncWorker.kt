package com.crowdmesh.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.crowdmesh.domain.repository.IdentityProvider
import com.crowdmesh.domain.repository.PresenceRepository
import com.crowdmesh.mesh.IdentityPayloadEncoder
import com.crowdmesh.mesh.discovery.PeerDiscoveryManager
import com.crowdmesh.mesh.peer.ConnectionManager
import com.crowdmesh.mesh.sync.SyncManager
import com.crowdmesh.mesh.transport.MeshDutyCycle
import com.crowdmesh.util.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The "periodic background work (if Android permits)" wake-up: one short,
 * bounded discovery+gossip burst, gated by WorkManager's own battery/network
 * constraints (see [com.crowdmesh.di.WorkManagerModule] for the schedule).
 * This never runs continuously — it does one burst and returns.
 */
@HiltWorker
class PeriodicMeshSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val peerDiscoveryManager: PeerDiscoveryManager,
    private val connectionManager: ConnectionManager,
    private val syncManager: SyncManager,
    private val identityProvider: IdentityProvider,
    private val presenceRepository: PresenceRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            withTimeoutOrNull(BURST_BUDGET_MILLIS) {
                val deviceId = identityProvider.getOrCreateDeviceId()
                val ownVersion = presenceRepository.getRecord(deviceId)?.version ?: 0L
                peerDiscoveryManager.startAdvertising(IdentityPayloadEncoder.encode(deviceId, ownVersion))

                peerDiscoveryManager.discover(MeshDutyCycle.BACKGROUND_BURST).collect { peer ->
                    connectionManager.connectIfWorthwhile(peer) { connection ->
                        syncManager.syncOverConnection(connection)
                    }
                }
                peerDiscoveryManager.stopAdvertising()
            }
            Result.success()
        } catch (e: Exception) {
            Logger.w(TAG, "periodic mesh sync burst failed", e)
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "crowdmesh_periodic_sync"
        private const val TAG = "PeriodicMeshSyncWorker"
        private const val BURST_BUDGET_MILLIS = 25_000L
    }
}
