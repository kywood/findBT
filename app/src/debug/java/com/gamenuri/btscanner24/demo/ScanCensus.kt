package com.gamenuri.btscanner24.demo

import android.util.Log
import com.gamenuri.btscanner24.core.ble.DeviceRepository
import com.gamenuri.btscanner24.core.model.BleDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Debug-only scan census for #82: every 15 s, logcat (tag NearoCensus) gets counts of what the
 * Nearby list holds, by category and by advertising source. Counts only: no names, addresses or
 * raw bytes leave this function, and nothing is sent anywhere (#19).
 *
 *   adb logcat -s NearoCensus
 */
internal object ScanCensus {
    private const val TAG = "NearoCensus"
    private const val PERIOD_MS = 15_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    fun start(repository: DeviceRepository) {
        if (started) return
        started = true
        scope.launch {
            var last = ""
            while (isActive) {
                delay(PERIOD_MS)
                val report = report(repository.devices.value.values)
                if (report != last) {
                    report.lines().forEach { Log.i(TAG, it) }
                    last = report
                }
            }
        }
    }

    private fun report(devices: Collection<BleDevice>): String = buildString {
        appendLine("total=${devices.size}")
        val named = devices.count { !it.name.isNullOrBlank() || !it.modelName.isNullOrBlank() }
        appendLine("named=$named unnamed=${devices.size - named}")
        appendLine("category " + counts(devices.map { it.category.name }))
        val unnamed = devices.filter { it.name.isNullOrBlank() && it.modelName.isNullOrBlank() }
        appendLine("category(unnamed) " + counts(unnamed.map { it.category.name }))
        val companies = devices.map { it.companyId?.let { id -> "0x%04X".format(id) } ?: "none" }
        appendLine("company " + counts(companies, top = 12))
        appendLine("source " + counts(devices.flatMap { sources(it) }, top = 20))
        val services = devices.flatMap { d -> d.serviceUuids16.map { "0x%04X".format(it) } }
        appendLine("service16 " + counts(services, top = 12))
        // Per category: which advertising families make it up (to spot other people's phones etc.).
        devices.groupBy { it.category }.forEach { (category, list) ->
            appendLine("mix ${category.name} " + counts(list.flatMap { sources(it) }, top = 8))
        }
    }

    /** Which advertising family a device belongs to, from its manufacturer data type byte. */
    private fun sources(device: BleDevice): List<String> {
        val out = mutableListOf<String>()
        manufacturerData(device.rawBytes).forEach { (company, payload) ->
            val type = payload.firstOrNull()
            out += when (company) {
                0x004C -> "apple:" + when (type) {
                    0x07 -> "proximityPairing"
                    0x09 -> "airplayTarget"
                    0x0C -> "handoff"
                    0x0F -> "nearbyAction"
                    0x10 -> "nearbyInfo"
                    0x12 -> "findMy"
                    0x16 -> "airplaySource"
                    null -> "empty"
                    else -> "0x%02X".format(type)
                }
                0x0006 -> "microsoft:" + when (type) {
                    0x01 -> "cdp"
                    0x03 -> "swiftPair"
                    null -> "empty"
                    else -> "0x%02X".format(type)
                }
                0x0075 -> "samsung:" + (type?.let { "0x%02X".format(it) } ?: "empty")
                0x00E0 -> "google"
                else -> "other"
            }
        }
        device.serviceUuids16.forEach {
            when (it) {
                0xFE2C -> out += "google:fastPair"
                0xFEF3 -> out += "google:nearby"
                0xFD5A -> out += "samsung:smartTag"
                0xFD69 -> out += "samsung:findMy"
                0xFE9F -> out += "google:fe9f"
            }
        }
        if (out.isEmpty()) out += "noVendorData"
        return out
    }

    /** (company ID, payload without the ID) for each manufacturer-specific AD structure. */
    private fun manufacturerData(hex: String): List<Pair<Int, List<Int>>> {
        val bytes = hex.split(':').mapNotNull { it.toIntOrNull(16) }
        val out = mutableListOf<Pair<Int, List<Int>>>()
        var i = 0
        while (i < bytes.size) {
            val length = bytes[i]
            if (length == 0 || i + length >= bytes.size + 1) break
            val type = bytes.getOrNull(i + 1)
            if (type == 0xFF && length >= 3) {
                val company = bytes[i + 2] or (bytes[i + 3] shl 8)
                out += company to bytes.subList(i + 4, minOf(i + 1 + length, bytes.size))
            }
            i += length + 1
        }
        return out
    }

    private fun counts(values: List<String>, top: Int = 30): String = values.groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }.take(top).joinToString(" ") { "${it.key}=${it.value}" }
}
