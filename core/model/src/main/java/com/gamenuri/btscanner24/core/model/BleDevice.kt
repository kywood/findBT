package com.gamenuri.btscanner24.core.model

/**
 * A BLE device seen during a scan. Display formatting (time, RSSI label) lives in the UI layer.
 */
data class BleDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val firstSeen: Long,
    val lastSeen: Long,
    val manufacturer: String = Constants.UNKNOWN,
    val deviceType: String = Constants.UNKNOWN,
    val uuids: List<String> = emptyList(),
    val txPower: Int? = null,
    val flags: String = Constants.UNKNOWN,
    val rawBytes: String = Constants.UNKNOWN,
) {
    val hasName: Boolean get() = !name.isNullOrBlank() && name != Constants.UNKNOWN

    val signalLevel: SignalLevel
        get() = when {
            rssi >= -60 -> SignalLevel.STRONG
            rssi >= -75 -> SignalLevel.MEDIUM
            else -> SignalLevel.WEAK
        }
}

enum class SignalLevel { STRONG, MEDIUM, WEAK }
