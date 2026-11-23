package com.gamenuri.btscanner24.core.model

/**
 * A BLE device seen during a scan. Display formatting (time, RSSI label) lives in the UI layer.
 */
data class BleDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    /**
     * Kalman-smoothed RSSI, the same filter find mode uses; drives "nearest" sorting, signal bars and
     * [distanceMeters] so the list and the find screen agree.
     */
    val smoothedRssi: Double = rssi.toDouble(),
    /** Approximate distance from [smoothedRssi]; null until the repository has estimated it. */
    val distanceMeters: Double? = null,
    val firstSeen: Long,
    val lastSeen: Long,
    /** Company name from Bluetooth SIG data, null while unknown. */
    val manufacturer: String? = null,
    /** Raw SIG company ID from manufacturer data, shown in the Advanced section. */
    val companyId: Int? = null,
    val category: DeviceCategory = DeviceCategory.UNKNOWN,
    val uuids: List<String> = emptyList(),
    /** 16-bit service UUIDs, e.g. 0x1802 Immediate Alert decides whether "Play sound" is offered. */
    val serviceUuids16: List<Int> = emptyList(),
    val txPower: Int? = null,
    val flags: String = Constants.UNKNOWN,
    val rawBytes: String = Constants.UNKNOWN,
) {
    val hasName: Boolean get() = !name.isNullOrBlank() && name != Constants.UNKNOWN

    val signalLevel: SignalLevel
        get() = when {
            smoothedRssi >= -60 -> SignalLevel.STRONG
            smoothedRssi >= -75 -> SignalLevel.MEDIUM
            else -> SignalLevel.WEAK
        }
}

enum class SignalLevel { STRONG, MEDIUM, WEAK }
