package com.gamenuri.btscanner24.feature.scan

import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.CategoryGroup
import com.gamenuri.btscanner24.core.model.DeviceFilter
import com.gamenuri.btscanner24.core.model.DeviceRelevance
import com.gamenuri.btscanner24.core.model.DeviceRelevanceRules
import com.gamenuri.btscanner24.core.model.DeviceSort
import com.gamenuri.btscanner24.core.model.DistanceBucket

/** Result of [DeviceListOrdering.filter]: personal devices first, then other devices when shown. */
data class FilteredDevices(
    val personal: List<BleDevice>,
    val other: List<BleDevice> = emptyList(),
    /** Other devices that match but are hidden because "Show other devices" is off. */
    val hiddenOther: Int = 0,
) {
    val all: List<BleDevice> get() = personal + other
}

/**
 * Pure list logic for the scan screen, free of Android types so it can be unit tested.
 *
 * Rule from the plan: while scanning, rows never move. Values update in place, new devices are
 * appended at the bottom, and the list is re-sorted only when the user asks (or changes sort/filter).
 */
object DeviceListOrdering {

    const val GONE_AFTER_MS = 30_000L

    fun isGone(device: BleDevice, now: Long): Boolean = now - device.lastSeen > GONE_AFTER_MS

    /**
     * Applies [filter] and the search [query]. Personal devices come back in [FilteredDevices.personal];
     * other devices (#82) in [FilteredDevices.other] only while "Show other devices" is on, otherwise
     * they are just counted in [FilteredDevices.hiddenOther].
     */
    fun filter(
        devices: Collection<BleDevice>,
        filter: DeviceFilter,
        query: String,
        now: Long,
        paired: Set<String> = emptySet(),
    ): FilteredDevices {
        val q = query.trim()
        // Searching, or picking the "Other" type chip, shows other devices whatever the switch says.
        val showOther = filter.showOther || q.isNotEmpty() || CategoryGroup.OTHER in filter.types
        val personal = mutableListOf<BleDevice>()
        val other = mutableListOf<BleDevice>()
        var hiddenOther = 0
        for (device in devices) {
            if (!matches(device, filter, q, now)) continue
            when (DeviceRelevanceRules.of(device, isPaired = device.address in paired)) {
                DeviceRelevance.PERSONAL -> personal += device
                DeviceRelevance.OTHER -> if (showOther) other += device else hiddenOther++
            }
        }
        return FilteredDevices(personal, other, hiddenOther)
    }

    private fun matches(device: BleDevice, filter: DeviceFilter, q: String, now: Long): Boolean {
        val distance = device.distanceMeters
        return (filter.showUnnamed || device.hasName || device.modelName != null) &&
            (filter.types.isEmpty() || device.category.filterGroup in filter.types) &&
            (filter.distances.isEmpty() || (distance != null && DistanceBucket.of(distance) in filter.distances)) &&
            (filter.signals.isEmpty() || device.signalLevel in filter.signals) &&
            (filter.manufacturers.isEmpty() || device.manufacturer in filter.manufacturers) &&
            (!filter.hideGone || !isGone(device, now)) &&
            (
                q.isEmpty() ||
                    device.name?.contains(q, ignoreCase = true) == true ||
                    device.modelName?.contains(q, ignoreCase = true) == true ||
                    device.manufacturer?.contains(q, ignoreCase = true) == true
                )
    }

    fun sort(devices: List<BleDevice>, sort: DeviceSort): List<BleDevice> = when (sort) {
        DeviceSort.NEAREST -> devices.sortedByDescending { it.smoothedRssi }
        DeviceSort.NAME -> devices.sortedWith(
            compareBy<BleDevice> { !it.hasName && it.modelName == null }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.modelName ?: it.name.orEmpty() },
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
