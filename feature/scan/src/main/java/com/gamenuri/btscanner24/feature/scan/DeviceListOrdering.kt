package com.gamenuri.btscanner24.feature.scan

import com.gamenuri.btscanner24.core.model.BleDevice

/**
 * Pure list logic for the scan screen, kept free of Android types so it can be unit tested.
 */
object DeviceListOrdering {

    fun filter(devices: Collection<BleDevice>, filter: ScanFilter): List<BleDevice> {
        val query = filter.query.trim()
        return devices.filter { device ->
            (!filter.namedOnly || device.hasName) &&
                (
                    query.isEmpty() ||
                        device.name?.contains(query, ignoreCase = true) == true ||
                        device.address.contains(query, ignoreCase = true)
                    )
        }
    }

    fun sort(devices: List<BleDevice>, sortType: SortType): List<BleDevice> = when (sortType) {
        SortType.FIRST_SEEN -> devices.sortedBy { it.firstSeen }
        SortType.RSSI -> devices.sortedByDescending { it.rssi }
    }

    /**
     * When the order is locked, keep devices where they already are and append new ones
     * at the bottom (in first-seen order), so the list does not jump while the user reads it.
     */
    fun keepOrder(previousOrder: List<String>, devices: List<BleDevice>): List<BleDevice> {
        val byAddress = devices.associateBy { it.address }
        val kept = previousOrder.mapNotNull { byAddress[it] }
        val keptAddresses = kept.mapTo(HashSet()) { it.address }
        val added = devices.filter { it.address !in keptAddresses }.sortedBy { it.firstSeen }
        return kept + added
    }
}
