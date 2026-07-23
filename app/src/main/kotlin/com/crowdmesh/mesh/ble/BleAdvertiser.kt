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
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.crowdmesh.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advertises a tiny, connectable BLE beacon: our service UUID plus a short
 * (few-byte) identity payload, nothing more. Real data only moves once a
 * peer decides to open a GATT connection based on this beacon.
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
        advertiser = leAdvertiser

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
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
