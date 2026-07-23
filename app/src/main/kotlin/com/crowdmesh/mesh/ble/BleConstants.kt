package com.crowdmesh.mesh.ble

import java.util.UUID

object BleConstants {
    /** App-specific GATT service. Replace with your own generated UUID before any real deployment. */
    val SERVICE_UUID: UUID = UUID.fromString("6f5a2b10-6a41-4b8b-9a8e-2f6f6f5a2b10")

    /** Central (scanner/connector) writes mesh frames here; server reassembles and processes them. */
    val INBOUND_CHARACTERISTIC_UUID: UUID = UUID.fromString("6f5a2b11-6a41-4b8b-9a8e-2f6f6f5a2b10")

    /** Server notifies the connected central with response frames on this characteristic. */
    val OUTBOUND_CHARACTERISTIC_UUID: UUID = UUID.fromString("6f5a2b12-6a41-4b8b-9a8e-2f6f6f5a2b10")

    /** Standard Client Characteristic Configuration Descriptor, used to enable notifications. */
    val CLIENT_CONFIG_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** 0xFFFF is reserved by the Bluetooth SIG for testing/prototyping — swap for a registered Company ID for production. */
    const val MANUFACTURER_ID: Int = 0xFFFF

    const val GATT_MTU_REQUEST_BYTES: Int = 217
}
