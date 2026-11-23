package com.gamenuri.btscanner24.feature.scan

import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.DeviceSort
import com.gamenuri.btscanner24.core.model.ListPreferences

/**
 * Pure list logic for the scan screen, free of Android types so it can be unit tested.
 *
 * Rule from the plan: while scanning, rows never move. Values update in place, new devices are
 * appended at the bottom, and the list is re-sorted only when the user asks (or changes sort/filter).
 */
object DeviceListOrdering {

    const val GONE_AFTER_MS = 30_000L

    fun isGone(device: BleDevice, now: Long): Boolean = now - device.lastSeen > GONE_AFTER_MS

    fun filter(devices: Collection<BleDevice>, prefs: ListPreferences, query: String, now: Long): List<BleDevice> {
        val q = query.trim()
        return devices.filter { device ->
            (!prefs.namedOnly || device.hasName) &&
                (prefs.groups.isEmpty() || device.category.filterGroup in prefs.groups) &&
                (!prefs.hideGone || !isGone(device, now)) &&
                (
                    q.isEmpty() ||
                        device.name?.contains(q, ignoreCase = true) == true ||
                        device.manufacturer?.contains(q, ignoreCase = true) == true
                    )
        }
    }

    fun sort(devices: List<BleDevice>, sort: DeviceSort): List<BleDevice> = when (sort) {
        DeviceSort.NEAREST -> devices.sortedByDescending { it.smoothedRssi }
        DeviceSort.NAME -> devices.sortedWith(
            compareBy<BleDevice> { !it.hasName }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() },
        )
        DeviceSort.RECENT -> devices.sortedByDescending { it.firstSeen }
    }

    /** Keeps rows where they already are and appends unseen ones at the bottom, oldest first. */
    fun keepOrder(previousOrder: List<String>, devices: List<BleDevice>): List<BleDevice> {
        val byAddress = devices.associateBy { it.address }
        val kept = previousOrder.mapNotNull { byAddress[it] }
        val keptAddresses = kept.mapTo(HashSet()) { it.address }
        val added = devices.filter { it.address !in keptAddresses }.sortedBy { it.firstSeen }
        return kept + added
    }
}
