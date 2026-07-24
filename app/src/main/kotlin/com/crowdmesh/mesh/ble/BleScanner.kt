package com.crowdmesh.mesh.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.crowdmesh.domain.model.TransportKind
import com.crowdmesh.mesh.IdentityPayloadEncoder
import com.crowdmesh.mesh.discovery.DiscoveredPeer
import com.crowdmesh.util.Logger
import com.crowdmesh.util.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
) {
    private val bluetoothManager by lazy { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }

    fun isSupported(): Boolean = bluetoothManager.adapter?.bluetoothLeScanner != null

    /**
     * Scans until the collector cancels (the caller — [com.crowdmesh.mesh.ble.AdaptiveScanScheduler] —
     * controls how long that is, e.g. a short burst). [scanMode] should be one of
     * `ScanSettings.SCAN_MODE_*`, chosen by the caller based on foreground/idle state.
     */
    @SuppressLint("MissingPermission")
    fun scan(scanMode: Int): Flow<DiscoveredPeer> = callbackFlow {
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner
        if (scanner == null || !hasScanPermission()) {
            Logger.w(TAG, "cannot scan: unsupported or missing BLUETOOTH_SCAN permission")
            close()
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                Logger.d(TAG, "[BLE_SCAN] discovered ${result.device.address} rssi=${result.rssi}")
                trySend(
                    DiscoveredPeer(
                        connectionHandle = result.device.address,
                        transportKind = TransportKind.BLE,
                        rssi = result.rssi,
                        timestampMillis = timeProvider.nowMillis(),
                    )
                )
            }

            override fun onScanFailed(errorCode: Int) {
                Logger.w(TAG, "scan failed, error=$errorCode")
                close(IllegalStateException("BLE scan failed with code $errorCode"))
            }
        }

        // Filters by manufacturer ID rather than the GATT service UUID: advertising both
        // the 128-bit service UUID and this manufacturer data would exceed legacy BLE's
        // 31-byte advertisement cap (see BleAdvertiser.start()'s comment). An all-zero
        // mask means "match any payload bytes" — we only care that some CrowdMesh peer
        // advertised under our manufacturer ID; the real service UUID is verified after
        // connecting, during GATT service discovery.
        val filters = listOf(
            ScanFilter.Builder()
                .setManufacturerData(
                    BleConstants.MANUFACTURER_ID,
                    ByteArray(IdentityPayloadEncoder.PAYLOAD_BYTES),
                    ByteArray(IdentityPayloadEncoder.PAYLOAD_BYTES),
                )
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(scanMode)
            .build()

        scanner.startScan(filters, settings, callback)
        awaitClose { scanner.stopScan(callback) }
    }

    private fun hasScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private companion object {
        const val TAG = "BleScanner"
    }
}
