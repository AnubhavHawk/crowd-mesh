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
    suspend fun connect(deviceAddress: String): Session? = suspendCancellableCoroutine { continuation ->
        val device = bluetoothManager.adapter?.getRemoteDevice(deviceAddress)
        if (device == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

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
                gatt.discoverServices()
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Logger.w(TAG, "service discovery failed, status=$status")
                    resumeOnce(null)
                    return
                }
                val service = gatt.getService(BleConstants.SERVICE_UUID)
                val inbound = service?.getCharacteristic(BleConstants.INBOUND_CHARACTERISTIC_UUID)
                val outbound = service?.getCharacteristic(BleConstants.OUTBOUND_CHARACTERISTIC_UUID)
                if (inbound == null || outbound == null) {
                    Logger.w(TAG, "peer is missing the CrowdMesh GATT service/characteristics")
                    resumeOnce(null)
                    return
                }

                gatt.setCharacteristicNotification(outbound, true)
                outbound.getDescriptor(BleConstants.CLIENT_CONFIG_DESCRIPTOR_UUID)?.let { descriptor ->
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }

                resumeOnce(Session(gatt, inbound, frameChannel.receiveAsFlow(), ackHolder))
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (characteristic.uuid == BleConstants.OUTBOUND_CHARACTERISTIC_UUID) {
                    val complete = runCatching { reassembler.feed(characteristic.value) }
                        .onFailure { Logger.w(TAG, "malformed frame from $deviceAddress", it) }
                        .getOrNull()
                    if (complete != null) frameChannel.trySend(complete)
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (characteristic.uuid == BleConstants.INBOUND_CHARACTERISTIC_UUID) {
                    ackHolder.pending?.complete(status == BluetoothGatt.GATT_SUCCESS)
                }
            }
        }

        val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) {
            continuation.resume(null)
        } else {
            continuation.invokeOnCancellation { gatt.close() }
        }
    }

    private companion object {
        const val TAG = "BleGattClientManager"
        const val WRITE_TIMEOUT_MILLIS = 5_000L
    }
}
