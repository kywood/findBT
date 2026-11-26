package com.gamenuri.btscanner24.feature.devices

import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.LastSeen
import com.gamenuri.btscanner24.core.model.OwnDevices
import com.gamenuri.btscanner24.core.model.PairedDevice
import com.gamenuri.btscanner24.core.model.SavedDevice

/**
 * Builds the My Devices tab from paired and saved devices and what the scan hears. Which sighting
 * belongs to which device is decided by [OwnDevices], the same for every part of the app.
 */
object MyDevices {
    const val ACTIVE_WINDOW_MS = OwnDevices.ACTIVE_WINDOW_MS

    /** The kept record is written at most once a minute, so it may trail the live sighting by that much. */
    private const val RECORD_SLACK_MS = 2 * 60_000L

    /** Up to here "Last seen: n min" from the live scan reads better than a date. */
    private const val RECORD_SHOWN_AFTER_MS = 60 * 60_000L

    /**
     * Whether a device that is not here shows its kept last-seen [record] (#162): it is at least as new
     * as anything heard this run ([heardAt], null when nothing was), and over an hour old. Not heard
     * this run is a plain null, never a sentinel: `Long.MIN_VALUE - slack` wrapped round and hid the line.
     */
    fun showsLastSeen(record: LastSeen, heardAt: Long?, now: Long): Boolean =
        (heardAt == null || record.seenAt >= heardAt - RECORD_SLACK_MS) &&
            now - record.seenAt >= RECORD_SHOWN_AFTER_MS

    fun build(
        paired: List<PairedDevice>,
        seen: Collection<BleDevice>,
        hidden: Set<String>,
        now: Long,
        saved: List<SavedDevice> = emptyList(),
        connected: Set<String> = emptySet(),
        distanceOf: (BleDevice) -> Double?,
    ): MyDevicesUiState {
        val sightings = OwnDevices.Sightings(seen)
        val items = OwnDevices.list(paired, saved, hidden).map { own ->
            val sighting = sightings.of(own)
            val heard = sighting != null && now - sighting.lastSeen <= ACTIVE_WINDOW_MS
            // A connected device is within Bluetooth range even when it sends nothing to measure (#119).
            val isConnected = own.device.address in connected
            MyDeviceItem(
                paired = own.device,
                sighting = sighting,
                isNearby = heard || isConnected,
                distanceMeters = sighting?.takeIf { heard }?.let(distanceOf),
                isSaved = own.isSaved,
                isConnected = isConnected,
            )
        }
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
