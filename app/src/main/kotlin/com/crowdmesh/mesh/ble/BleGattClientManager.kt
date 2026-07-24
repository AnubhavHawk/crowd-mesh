@file:Suppress("DEPRECATION")

package com.crowdmesh.mesh.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothDevice
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * The central (GATT client) side of a mesh connection: the device that
 * scanned and found a peer's advertisement is the one that opens the
 * connection and drives the exchange.
 */
@Singleton
class BleGattClientManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val bluetoothManager by lazy { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }

    /** A live, service-discovered connection ready to exchange mesh frames. */
    class Session internal constructor(
        private val gatt: BluetoothGatt,
        private val inboundCharacteristic: BluetoothGattCharacteristic,
        val incomingFrames: Flow<ByteArray>,
        private val ackHolder: AckHolder,
    ) {
        @SuppressLint("MissingPermission")
        suspend fun send(message: ByteArray): Boolean {
            for (chunk in MessageFramer.encodeChunks(message)) {
                val ack = CompletableDeferred<Boolean>()
                ackHolder.pending = ack

                inboundCharacteristic.value = chunk
                val started = gatt.writeCharacteristic(inboundCharacteristic)
                if (!started) return false

                val acked = withTimeoutOrNull(WRITE_TIMEOUT_MILLIS) { ack.await() } ?: false
                if (!acked) return false
            }
            return true
        }

        @SuppressLint("MissingPermission")
        fun close() {
            gatt.disconnect()
            gatt.close()
        }
    }

    internal class AckHolder {
        var pending: CompletableDeferred<Boolean>? = null
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(deviceAddress: String): Session? =
        withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) { connectInternal(deviceAddress) }.also { session ->
            if (session == null) Logger.w(TAG, "[GATT_CLIENT] connect($deviceAddress) timed out or failed after ${CONNECT_TIMEOUT_MILLIS}ms")
        }

    @SuppressLint("MissingPermission")
    private suspend fun connectInternal(deviceAddress: String): Session? = suspendCancellableCoroutine { continuation ->
        if (!hasConnectPermission()) {
            Logger.w(TAG, "[GATT_CLIENT] missing BLUETOOTH_CONNECT permission, not connecting to $deviceAddress")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val device = bluetoothManager.adapter?.getRemoteDevice(deviceAddress)
        if (device == null) {
            Logger.w(TAG, "[GATT_CLIENT] getRemoteDevice($deviceAddress) returned null")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        Logger.d(TAG, "[GATT_CLIENT] connectGatt($deviceAddress) initiating")

        var pendingInboundCharacteristic: BluetoothGattCharacteristic? = null
        val reassembler = MessageFramer.Reassembler()
        // A Channel (not a SharedFlow) so frames arriving before the returned Session's
        // incomingFrames is actually collected are queued rather than dropped — there is
        // exactly one logical consumer (SyncManager) per connection, so this is a perfect fit.
        val frameChannel = Channel<ByteArray>(Channel.UNLIMITED)
        val ackHolder = AckHolder()
        var resumed = false

        fun resumeOnce(session: Session?) {
            if (!resumed && continuation.isActive) {
                resumed = true
                continuation.resume(session)
            }
        }

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                Logger.d(TAG, "[GATT_CLIENT] onConnectionStateChange($deviceAddress) status=$status newState=$newState")
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> gatt.requestMtu(BleConstants.GATT_MTU_REQUEST_BYTES)
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        ackHolder.pending?.complete(false)
                        frameChannel.close()
                        resumeOnce(null)
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                Logger.d(TAG, "[GATT_CLIENT] onMtuChanged($deviceAddress) mtu=$mtu status=$status")
                gatt.discoverServices()
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Logger.w(TAG, "[GATT_CLIENT] service discovery failed for $deviceAddress, status=$status")
                    resumeOnce(null)
                    return
                }
                val service = gatt.getService(BleConstants.SERVICE_UUID)
                val inbound = service?.getCharacteristic(BleConstants.INBOUND_CHARACTERISTIC_UUID)
                val outbound = service?.getCharacteristic(BleConstants.OUTBOUND_CHARACTERISTIC_UUID)
                if (inbound == null || outbound == null) {
                    Logger.w(TAG, "[GATT_CLIENT] peer $deviceAddress is missing the CrowdMesh GATT service/characteristics (service=$service)")
                    resumeOnce(null)
                    return
                }

                gatt.setCharacteristicNotification(outbound, true)
                val descriptor = outbound.getDescriptor(BleConstants.CLIENT_CONFIG_DESCRIPTOR_UUID)
                if (descriptor == null) {
                    // Nothing to wait for — resume immediately, same as before.
                    Logger.w(TAG, "[GATT_CLIENT] outbound characteristic missing CCCD descriptor for $deviceAddress")
                    Logger.d(TAG, "[GATT_CLIENT] session ready for $deviceAddress")
                    resumeOnce(Session(gatt, inbound, frameChannel.receiveAsFlow(), ackHolder))
                    return
                }

                // Android's BLE stack only allows one outstanding GATT operation at a time
                // per connection. Resuming here (before this descriptor write actually
                // completes) let SyncManager's first characteristic write — the HELLO —
                // race the still-in-flight descriptor write and get rejected outright
                // (gatt.writeCharacteristic returning false). Only resume once
                // onDescriptorWrite below confirms this operation is done.
                pendingInboundCharacteristic = inbound
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                Logger.d(TAG, "[GATT_CLIENT] onDescriptorWrite for $deviceAddress status=$status")
                val inbound = pendingInboundCharacteristic ?: return
                pendingInboundCharacteristic = null
                Logger.d(TAG, "[GATT_CLIENT] session ready for $deviceAddress")
                resumeOnce(Session(gatt, inbound, frameChannel.receiveAsFlow(), ackHolder))
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (characteristic.uuid == BleConstants.OUTBOUND_CHARACTERISTIC_UUID) {
                    Logger.d(TAG, "[GATT_CLIENT] onCharacteristicChanged from $deviceAddress, ${characteristic.value?.size ?: 0} bytes")
                    val complete = runCatching { reassembler.feed(characteristic.value) }
                        .onFailure { Logger.w(TAG, "malformed frame from $deviceAddress", it) }
                        .getOrNull()
                    if (complete != null) {
                        Logger.d(TAG, "[GATT_CLIENT] reassembled ${complete.size}-byte frame from $deviceAddress")
                        frameChannel.trySend(complete)
                    }
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (characteristic.uuid == BleConstants.INBOUND_CHARACTERISTIC_UUID) {
                    Logger.d(TAG, "[GATT_CLIENT] onCharacteristicWrite to $deviceAddress status=$status")
                    ackHolder.pending?.complete(status == BluetoothGatt.GATT_SUCCESS)
                }
            }
        }

        val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) {
            Logger.w(TAG, "[GATT_CLIENT] connectGatt($deviceAddress) returned null")
            continuation.resume(null)
        } else {
            continuation.invokeOnCancellation { gatt.close() }
        }
    }

    private fun hasConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private companion object {
        const val TAG = "BleGattClientManager"
        const val WRITE_TIMEOUT_MILLIS = 5_000L
        const val CONNECT_TIMEOUT_MILLIS = 15_000L
    }
}
