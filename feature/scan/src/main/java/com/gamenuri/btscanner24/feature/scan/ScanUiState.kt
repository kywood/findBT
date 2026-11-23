package com.gamenuri.btscanner24.feature.scan

import com.gamenuri.btscanner24.core.ble.ScanFailure
import com.gamenuri.btscanner24.core.model.BleDevice
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
)

data class ScanUiState(
    val status: ScanStatus = ScanStatus.Idle,
    val items: List<DeviceListItem> = emptyList(),
    val preferences: ListPreferences = ListPreferences(),
    val query: String = "",
    /** The sorted order differs from what is on screen; show the "Refresh order" button. */
    val reorderAvailable: Boolean = false,
) {
    val isScanning: Boolean get() = status == ScanStatus.Scanning
}
