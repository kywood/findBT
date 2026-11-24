package com.gamenuri.btscanner24.feature.scan

import com.gamenuri.btscanner24.core.ble.ScanFailure
import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.DeviceLabel
import com.gamenuri.btscanner24.core.model.DeviceNaming
import com.gamenuri.btscanner24.core.model.ListPreferences

sealed interface ScanStatus {
    data object Idle : ScanStatus
    data object Scanning : ScanStatus
    data class Error(val reason: ScanFailure) : ScanStatus
}

/** One row of the device list. */
data class DeviceListItem(
    val device: BleDevice,
    /** Appeared after the last full sort; shown at the bottom with a "New" badge. */
    val isNew: Boolean,
    /** Not heard from for [DeviceListOrdering.GONE_AFTER_MS]; shown dimmed. */
    val isGone: Boolean,
    /** Rough distance from the smoothed signal, shown as "~3 m". */
    val distanceMeters: Double? = null,
    val label: DeviceLabel = DeviceNaming.label(device),
    /** Another row has the same name: add the address suffix. */
    val showSuffix: Boolean = false,
    /** Paired with this phone; the name is the phone's alias for it. */
    val isPaired: Boolean = false,
)

data class ScanUiState(
    val status: ScanStatus = ScanStatus.Idle,
    val items: List<DeviceListItem> = emptyList(),
    val preferences: ListPreferences = ListPreferences(),
    val query: String = "",
    /** The sorted order differs from what is on screen; show the floating "Order changed" pill. */
    val reorderAvailable: Boolean = false,
    /** The user paused the automatic scan; lasts until they leave the tab (#62). */
    val isPaused: Boolean = false,
    /** Devices seen before filters and search, for "2 / 15". */
    val totalCount: Int = 0,
) {
    val isScanning: Boolean get() = status == ScanStatus.Scanning

    /** Filters in use, for the badge on the filter button. Search is not counted. */
    val activeFilterCount: Int
        get() = preferences.groups.size + (if (preferences.namedOnly) 1 else 0) + (if (preferences.hideGone) 1 else 0)
}
