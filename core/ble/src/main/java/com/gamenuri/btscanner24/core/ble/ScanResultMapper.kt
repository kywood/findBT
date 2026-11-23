package com.gamenuri.btscanner24.core.ble

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.Constants
import com.gamenuri.btscanner24.core.model.DeviceCategory

/** Converts a platform [ScanResult] into a [BleDevice], keeping what earlier packets already told us. */
object ScanResultMapper {

    /** Weight of the newest sample in the list RSSI average (find mode has its own estimator). */
    private const val RSSI_ALPHA = 0.3

    @SuppressLint("MissingPermission")
    fun map(result: ScanResult, previous: BleDevice?, now: Long, companies: CompanyDirectory): BleDevice {
        val rawBytes = result.scanRecord?.bytes
        val parsed = if (rawBytes != null) BleParser.parse(rawBytes, companies) else ParsedAdvertiseData()

        // device.name needs BLUETOOTH_CONNECT on Android 12+; fall back to advertised name.
        val systemName = try {
            result.device.name
        } catch (_: SecurityException) {
            null
        }
        val name = systemName
            ?: result.scanRecord?.deviceName
            ?: parsed.name.takeIf { it != Constants.UNKNOWN }

        val next = BleDevice(
            address = result.device.address,
            name = name,
            rssi = result.rssi,
            smoothedRssi = previous?.let { it.smoothedRssi + RSSI_ALPHA * (result.rssi - it.smoothedRssi) }
                ?: result.rssi.toDouble(),
            firstSeen = previous?.firstSeen ?: now,
            lastSeen = now,
            manufacturer = parsed.manufacturer,
            companyId = parsed.companyId,
            category = parsed.category,
            serviceUuids16 = parsed.serviceUuids16,
            uuids = parsed.serviceUuids,
            txPower = parsed.txPower,
            flags = parsed.flags,
            rawBytes = parsed.rawBytes,
        )
        // The system name (e.g. "Galaxy Buds2") can arrive without any advertised category hint.
        val withName = if (next.category == DeviceCategory.UNKNOWN) {
            next.copy(category = DeviceCategoryResolver.fromName(name))
        } else {
            next
        }
        return mergeKnown(previous, withName)
    }

    /**
     * Devices alternate between packet types (e.g. iBeacon/manufacturer data, then a scan response
     * with only the name). A packet that lacks a field must not wipe out a value an earlier packet gave.
     */
    fun mergeKnown(previous: BleDevice?, next: BleDevice): BleDevice {
        if (previous == null) return next
        return next.copy(
            name = next.name?.takeIf { it.isNotBlank() } ?: previous.name,
            manufacturer = next.manufacturer ?: previous.manufacturer,
            companyId = next.companyId ?: previous.companyId,
            category = next.category.takeIf { it != DeviceCategory.UNKNOWN } ?: previous.category,
            uuids = next.uuids.ifEmpty { previous.uuids },
            serviceUuids16 = next.serviceUuids16.ifEmpty { previous.serviceUuids16 },
            txPower = next.txPower ?: previous.txPower,
            flags = next.flags.takeIf { it != Constants.UNKNOWN } ?: previous.flags,
        )
    }
}
