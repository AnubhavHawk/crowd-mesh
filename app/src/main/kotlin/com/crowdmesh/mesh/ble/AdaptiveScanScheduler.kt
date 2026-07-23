package com.crowdmesh.mesh.ble

import android.bluetooth.le.ScanSettings
import com.crowdmesh.mesh.discovery.DiscoveredPeer
import com.crowdmesh.util.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class ScanCadence(val scanMode: Int, val burstMillis: Long, val idleMillis: Long) {
    /** Right after app-open or an explicit "Update" tap: scan aggressively for a short while. */
    ACTIVE(ScanSettings.SCAN_MODE_LOW_LATENCY, burstMillis = 4_000L, idleMillis = 6_000L),

    /** App is open but idle: duty-cycle gently instead of scanning continuously. */
    FOREGROUND_IDLE(ScanSettings.SCAN_MODE_LOW_POWER, burstMillis = 3_000L, idleMillis = 45_000L),

    /** A single short burst per WorkManager execution; the caller owns the overall period (>=15 min). */
    BACKGROUND_BURST(ScanSettings.SCAN_MODE_LOW_POWER, burstMillis = 5_000L, idleMillis = 0L),
}

/**
 * Turns "never scan continuously" into concrete duty-cycled bursts. This is
 * the single knob that keeps CrowdMesh's radio usage bounded: nothing in the
 * mesh layer scans without going through here.
 */
@Singleton
class AdaptiveScanScheduler @Inject constructor(
    private val bleScanner: BleScanner,
) {
    /**
     * Emits discovered peers for as long as the collector stays subscribed,
     * duty-cycling scan on/off according to [cadence]. A [BACKGROUND_BURST]
     * cadence naturally completes after one burst (idleMillis = 0 means "don't loop").
     */
    fun discoverPeers(cadence: ScanCadence): Flow<DiscoveredPeer> = channelFlow {
        while (true) {
            Logger.d(TAG, "starting ${cadence.name} scan burst (${cadence.burstMillis}ms)")
            val burstJob = launch {
                bleScanner.scan(cadence.scanMode).collect { peer -> send(peer) }
            }
            delay(cadence.burstMillis)
            burstJob.cancel()

            if (cadence.idleMillis <= 0L) break
            delay(cadence.idleMillis)
        }
    }

    private companion object {
        const val TAG = "AdaptiveScanScheduler"
    }
}
