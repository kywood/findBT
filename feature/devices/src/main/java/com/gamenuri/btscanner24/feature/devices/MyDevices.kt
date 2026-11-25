package com.gamenuri.btscanner24.feature.devices

import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.PairedDevice
import com.gamenuri.btscanner24.core.model.SavedDevice

/**
 * Builds the My Devices tab from paired and saved devices and what the scan hears.
 *
 * Matching: the paired address first. Most earbuds and watches advertise BLE from a random address
 * that differs from the paired (classic) one, so the fallback is the same device name; the newest
 * sighting wins. A device the user renamed on the phone still advertises its own name, so that
 * one is tried as well (#116). The match rate is one of the prototype measurements. Saved devices
 * (#108) behave the same; they match on the name they advertised when saved, not on what the user
 * called them.
 */
object MyDevices {
    const val ACTIVE_WINDOW_MS = 30_000L

    fun build(
        paired: List<PairedDevice>,
        seen: Collection<BleDevice>,
        hidden: Set<String>,
        now: Long,
        saved: List<SavedDevice> = emptyList(),
        distanceOf: (BleDevice) -> Double?,
    ): MyDevicesUiState {
        val byAddress = seen.associateBy { it.address }
        val byName = seen.filter { it.hasName }.groupBy { it.name!!.trim().lowercase() }

        fun item(device: PairedDevice, matchNames: List<String>, isSaved: Boolean): MyDeviceItem {
            val sighting = byAddress[device.address]
                ?: matchNames.flatMap { byName[it.trim().lowercase()].orEmpty() }.maxByOrNull { it.lastSeen }
            val nearby = sighting != null && now - sighting.lastSeen <= ACTIVE_WINDOW_MS
            return MyDeviceItem(
                paired = device,
                sighting = sighting,
                isNearby = nearby,
                distanceMeters = sighting?.takeIf { nearby }?.let(distanceOf),
                isSaved = isSaved,
            )
        }
        val pairedAddresses = paired.mapTo(HashSet()) { it.address }
        val items = paired
            .filter { it.address !in hidden }
            .map { item(it, listOfNotNull(it.name, it.advertisedName), isSaved = false) } +
            // A device that is paired as well shows once, as paired.
            saved
                .filter { it.address !in pairedAddresses }
                .map { item(it.asPaired(), listOfNotNull(it.advertisedName), isSaved = true) }
        // Whatever the user saved by hand is something they look for: never in the collapsed group.
        val (priority, others) = items.partition { it.isSaved || it.paired.isPriority }
        val (heard, notNearby) = priority.partition { it.isNearby }
        // Saved devices that are here get their own section at the top; the rest wait in "Not nearby".
        val (savedNearby, nearby) = heard.partition { it.isSaved }
        return MyDevicesUiState(
            saved = savedNearby.sortedBy { it.distanceMeters ?: Double.MAX_VALUE },
            savedCount = items.count { it.isSaved },
            nearby = nearby.sortedBy { it.distanceMeters ?: Double.MAX_VALUE },
            notNearby = notNearby.sortedWith(
                compareByDescending<MyDeviceItem> { it.sighting?.lastSeen ?: Long.MIN_VALUE }
                    .thenBy { it.paired.name.orEmpty().lowercase() },
            ),
            others = others.sortedBy { it.paired.name.orEmpty().lowercase() },
            isEmpty = items.isEmpty(),
        )
    }
}
