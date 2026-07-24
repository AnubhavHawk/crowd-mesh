@file:Suppress("DEPRECATION")

package com.crowdmesh.mesh.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.crowdmesh.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The peripheral (GATT server) side of a mesh connection: accepts writes
 * from a connected central onto [BleConstants.INBOUND_CHARACTERISTIC_UUID],
 * reassembles them via [MessageFramer], and can push responses back over
 * [BleConstants.OUTBOUND_CHARACTERISTIC_UUID] notifications.
 */
@Singleton
class BleGattServerManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val bluetoothManager by lazy { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private var gattServer: BluetoothGattServer? = null

    private val reassemblers = ConcurrentHashMap<String, MessageFramer.Reassembler>()
    private val connectedDevices = ConcurrentHashMap<String, BluetoothDevice>()
    private val pendingNotificationAcks = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    // One Channel per connected device, created at CONNECTED time — guarantees no frame
    // is dropped even if the consumer (SyncManager, via BleTransport) starts collecting
    // slightly after the peer's first write arrives.
    private val perDeviceFrameChannels = ConcurrentHashMap<String, Channel<ByteArray>>()

    // Tracks which connected centrals have actually enabled notifications (written the
    // CCCD descriptor) yet — see the comment on _deviceConnected's emission below.
    private val notificationsEnabledDevices = ConcurrentHashMap.newKeySet<String>()

    /** Emits a device address whenever a central connects to us, so [com.crowdmesh.mesh.transport.BleTransport] can hand off a connection object to the sync layer. */
    private val _deviceConnected = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val deviceConnected: SharedFlow<String> = _deviceConnected.asSharedFlow()

    /** Frames received from [deviceAddress], queued from the moment it connects. Null if not currently connected. */
    fun framesFor(deviceAddress: String): Flow<ByteArray>? = perDeviceFrameChannels[deviceAddress]?.receiveAsFlow()

    private val callback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Logger.d(TAG, "[GATT_SERVER] onConnectionStateChange(${device.address}) status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevices[device.address] = device
                    reassemblers[device.address] = MessageFramer.Reassembler()
                    perDeviceFrameChannels[device.address] = Channel(Channel.UNLIMITED)
                    // _deviceConnected is NOT emitted here — see onDescriptorWriteRequest.
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevices.remove(device.address)
                    reassemblers.remove(device.address)
                    perDeviceFrameChannels.remove(device.address)?.close()
                    pendingNotificationAcks.remove(device.address)?.complete(false)
                    notificationsEnabledDevices.remove(device.address)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (characteristic.uuid == BleConstants.INBOUND_CHARACTERISTIC_UUID) {
                Logger.d(TAG, "[GATT_SERVER] onCharacteristicWriteRequest from ${device.address}, ${value.size} bytes")
                val reassembler = reassemblers.getOrPut(device.address) { MessageFramer.Reassembler() }
                val complete = runCatching { reassembler.feed(value) }
                    .onFailure { Logger.w(TAG, "malformed frame from ${device.address}", it) }
                    .getOrNull()
                if (complete != null) {
                    Logger.d(TAG, "[GATT_SERVER] reassembled ${complete.size}-byte frame from ${device.address}")
                    val sent = perDeviceFrameChannels[device.address]?.trySend(complete)
                    if (sent == null || sent.isFailure) {
                        Logger.w(TAG, "[GATT_SERVER] no open frame channel for ${device.address}, frame dropped")
                    }
                }
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            val notificationsEnabled = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            Logger.d(TAG, "[GATT_SERVER] onDescriptorWriteRequest from ${device.address}, notifications enabled=$notificationsEnabled")
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
            // Only now — once the central has actually subscribed to notifications — is it
            // safe to hand this connection to SyncManager, which immediately notifies a
            // HELLO. Emitting _deviceConnected at STATE_CONNECTED time (the old behavior)
            // let that first notification race the central's own CCCD write; a notification
            // sent before a central subscribes is routinely dropped by the BLE stack, which
            // silently killed the handshake before it could even start. Guarded with
            // notificationsEnabledDevices since some centrals redundantly re-write the CCCD.
            if (notificationsEnabled && notificationsEnabledDevices.add(device.address)) {
                _deviceConnected.tryEmit(device.address)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            Logger.d(TAG, "[GATT_SERVER] onNotificationSent to ${device.address} status=$status")
            pendingNotificationAcks.remove(device.address)?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (gattServer != null) return
        if (!hasConnectPermission()) {
            Logger.w(TAG, "missing BLUETOOTH_CONNECT permission, not opening GATT server")
            return
        }
        val server = bluetoothManager.openGattServer(context, callback)
        if (server == null) {
            Logger.w(TAG, "failed to open GATT server")
            return
        }

        val service = BluetoothGattService(BleConstants.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val inbound = BluetoothGattCharacteristic(
            BleConstants.INBOUND_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        val outbound = BluetoothGattCharacteristic(
            BleConstants.OUTBOUND_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        outbound.addDescriptor(
            BluetoothGattDescriptor(
                BleConstants.CLIENT_CONFIG_DESCRIPTOR_UUID,
                BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ,
            )
        )

        service.addCharacteristic(inbound)
        service.addCharacteristic(outbound)
        server.addService(service)
        gattServer = server
        Logger.d(TAG, "[GATT_SERVER] started, service ${BleConstants.SERVICE_UUID} registered")
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        gattServer?.close()
        gattServer = null
        connectedDevices.clear()
        reassemblers.clear()
        pendingNotificationAcks.clear()
        perDeviceFrameChannels.values.forEach { it.close() }
        perDeviceFrameChannels.clear()
    }

    /** Sends [message] to an already-connected central, chunked and flow-controlled via notify ACKs. */
    @SuppressLint("MissingPermission")
    suspend fun sendFrame(deviceAddress: String, message: ByteArray): Boolean {
        val server = gattServer
        if (server == null) {
            Logger.w(TAG, "[GATT_SERVER] sendFrame($deviceAddress) failed: server not started")
            return false
        }
        val device = connectedDevices[deviceAddress]
        if (device == null) {
            Logger.w(TAG, "[GATT_SERVER] sendFrame($deviceAddress) failed: not currently connected")
            return false
        }
        val characteristic = server.getService(BleConstants.SERVICE_UUID)
            ?.getCharacteristic(BleConstants.OUTBOUND_CHARACTERISTIC_UUID)
        if (characteristic == null) {
            Logger.w(TAG, "[GATT_SERVER] sendFrame($deviceAddress) failed: outbound characteristic missing")
            return false
        }

        for (chunk in MessageFramer.encodeChunks(message)) {
            val ack = CompletableDeferred<Boolean>()
            pendingNotificationAcks[deviceAddress] = ack

            characteristic.value = chunk
            val started = server.notifyCharacteristicChanged(device, characteristic, false)
            if (!started) {
                Logger.w(TAG, "[GATT_SERVER] notifyCharacteristicChanged($deviceAddress) returned false")
                pendingNotificationAcks.remove(deviceAddress)
                return false
            }

            val acked = withTimeoutOrNull(NOTIFY_TIMEOUT_MILLIS) { ack.await() } ?: false
            if (!acked) {
                Logger.w(TAG, "[GATT_SERVER] sendFrame($deviceAddress) chunk not acked within ${NOTIFY_TIMEOUT_MILLIS}ms")
                return false
            }
        }
        Logger.d(TAG, "[GATT_SERVER] sendFrame($deviceAddress) sent ${message.size} bytes successfully")
        return true
    }

    private fun hasConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private companion object {
        const val TAG = "BleGattServerManager"
        const val NOTIFY_TIMEOUT_MILLIS = 5_000L
    }
}
