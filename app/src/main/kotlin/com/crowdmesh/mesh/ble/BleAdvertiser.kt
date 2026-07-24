package com.crowdmesh.mesh.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.crowdmesh.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advertises a tiny, connectable BLE beacon: a manufacturer-ID-tagged short
 * identity payload, nothing more (the GATT service UUID itself is not part
 * of the advertisement — see the size-budget note in [start]). Real data
 * only moves once a peer decides to open a GATT connection based on this beacon.
 */
@Singleton
class BleAdvertiser @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val bluetoothManager by lazy { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private var advertiser: BluetoothLeAdvertiser? = null

    private val callback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Logger.d(TAG, "advertising started")
        }

        override fun onStartFailure(errorCode: Int) {
            Logger.w(TAG, "advertising failed to start, error=$errorCode")
        }
    }

    fun isSupported(): Boolean = bluetoothManager.adapter?.bluetoothLeAdvertiser != null

    @SuppressLint("MissingPermission")
    fun start(shortIdentityPayload: ByteArray) {
        if (!hasAdvertisePermission()) {
            Logger.w(TAG, "missing BLUETOOTH_ADVERTISE permission, not advertising")
            return
        }
        val leAdvertiser = bluetoothManager.adapter?.bluetoothLeAdvertiser
        if (leAdvertiser == null) {
            Logger.w(TAG, "BLE advertising unsupported on this device")
            return
        }
        // start() is called again on every notifyLocalRecordChanged() (e.g. an Update
        // tap) to broadcast the new record version — the OS tracks active advertise
        // sessions per-callback, so calling startAdvertising again with the same
        // `callback` while a previous session is still live fails with
        // ADVERTISE_FAILED_ALREADY_STARTED (error=3) instead of updating the payload.
        // Stop any existing session first so the new version actually gets broadcast.
        if (advertiser != null) {
            leAdvertiser.stopAdvertising(callback)
        }
        advertiser = leAdvertiser

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()

        // Deliberately NOT advertising the full 128-bit service UUID here: Flags(3B) +
        // a 128-bit Service UUID AD structure(18B) + this manufacturer data already
        // exceeds legacy BLE advertising's 31-byte cap (measured: 37 bytes), which fails
        // silently with ADVERTISE_FAILED_DATA_TOO_LARGE (error=1) on stacks that enforce
        // it strictly. The manufacturer-ID-tagged payload is enough for scan-time
        // filtering (see BleScanner); the real GATT service UUID is only checked after
        // connecting, during service discovery, where there's no such size limit.
        val data = AdvertiseData.Builder()
            .addManufacturerData(BleConstants.MANUFACTURER_ID, shortIdentityPayload)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        leAdvertiser.startAdvertising(settings, data, callback)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        advertiser?.stopAdvertising(callback)
        advertiser = null
    }

    private fun hasAdvertisePermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_ADVERTISE) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private companion object {
        const val TAG = "BleAdvertiser"
    }
}
