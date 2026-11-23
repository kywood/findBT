package com.gamenuri.btscanner24.feature.devices

import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.PairedDevice

/**
 * Builds the My Devices tab from paired devices and what the scan hears.
 *
 * Matching: the paired address first. Most earbuds and watches advertise BLE from a random address
 * that differs from the paired (classic) one, so the fallback is the same device name; the newest
 * sighting wins. The match rate is one of the prototype measurements.
 */
object MyDevices {
    const val ACTIVE_WINDOW_MS = 30_000L

    fun build(
        paired: List<PairedDevice>,
        seen: Collection<BleDevice>,
        hidden: Set<String>,
        now: Long,
        distanceOf: (BleDevice) -> Double?,
    ): MyDevicesUiState {
        val byAddress = seen.associateBy { it.address }
        val byName = seen.filter { it.hasName }.groupBy { it.name!!.trim().lowercase() }

        val items = paired
            .filter { it.address !in hidden }
            .map { device ->
                val sighting = byAddress[device.address]
                    ?: device.name?.trim()?.lowercase()?.let { key -> byName[key]?.maxByOrNull { it.lastSeen } }
                val nearby = sighting != null && now - sighting.lastSeen <= ACTIVE_WINDOW_MS
                MyDeviceItem(
                    paired = device,
                    sighting = sighting,
                    isNearby = nearby,
                    distanceMeters = sighting?.takeIf { nearby }?.let(distanceOf),
                )
            }
        val (priority, others) = items.partition { it.paired.isPriority }
        val (nearby, notNearby) = priority.partition { it.isNearby }
        return MyDevicesUiState(
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
