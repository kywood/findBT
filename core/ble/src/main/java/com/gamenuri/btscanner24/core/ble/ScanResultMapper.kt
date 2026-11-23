package com.gamenuri.btscanner24.core.ble

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.Constants

/** Converts a platform [ScanResult] into a [BleDevice], keeping firstSeen from an earlier sighting. */
object ScanResultMapper {

    @SuppressLint("MissingPermission")
    fun map(result: ScanResult, previous: BleDevice?, now: Long): BleDevice {
        val rawBytes = result.scanRecord?.bytes
        val parsed = if (rawBytes != null) BleParser.parse(rawBytes) else ParsedAdvertiseData()

        // device.name needs BLUETOOTH_CONNECT on Android 12+; fall back to advertised name.
        val systemName = try {
            result.device.name
        } catch (_: SecurityException) {
            null
        }
        val name = systemName
            ?: result.scanRecord?.deviceName
            ?: parsed.name.takeIf { it != Constants.UNKNOWN }
            ?: previous?.name

        return BleDevice(
            address = result.device.address,
            name = name,
            rssi = result.rssi,
            firstSeen = previous?.firstSeen ?: now,
            lastSeen = now,
            manufacturer = parsed.manufacturerId,
            deviceType = parsed.deviceType,
            uuids = parsed.serviceUuids,
            txPower = parsed.txPower,
            flags = parsed.flags,
            rawBytes = parsed.rawBytes,
        )
    }
}
