package com.gamenuri.findbt


import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val firstSeen: Long = System.currentTimeMillis(),
    val manufacturer: String = Constants.UNKNOWN,
    val deviceType: String = Constants.UNKNOWN,
    val uuids: List<String> = emptyList(),
    val txPower: Int? = null,
    val flags: String = Constants.UNKNOWN,
    val rawBytes: String = Constants.UNKNOWN
) {
    val firstSeenFormatted: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(firstSeen))
}