package com.gamenuri.btscanner24.feature.scan

import com.gamenuri.btscanner24.core.ble.ScanFailure
import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.DeviceLabel
import com.gamenuri.btscanner24.core.model.DeviceNaming
import com.gamenuri.btscanner24.core.model.ListPreferences
import com.gamenuri.btscanner24.core.model.MapSpot

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
    /** In the "Other devices" section below personal devices (#82). */
    val isOther: Boolean = false,
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
    /** Devices seen before filters and search. The status line counts only personal ones ([census]). */
    val totalCount: Int = 0,
    /** Other devices (#82) that match but are hidden while "Show other devices" is off. */
    val hiddenOtherCount: Int = 0,
    /** Personal/other split and makers of the whole scan, before filters (#82, #85). */
    val census: ScanCensus = ScanCensus(),
    /** Where each device still around stands on the 3D map (#86), by its address. */
    val mapSpots: Map<String, MapSpot> = emptyMap(),
) {
    val isScanning: Boolean get() = status == ScanStatus.Scanning

    /** Filters in use, for the badge on the filter button. Search is not counted. */
    val activeFilterCount: Int
        get() = preferences.filter.activeCount
}
