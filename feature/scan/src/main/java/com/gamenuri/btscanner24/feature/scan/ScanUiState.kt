package com.gamenuri.btscanner24.feature.scan

import com.gamenuri.btscanner24.core.ble.ScanFailure
import com.gamenuri.btscanner24.core.model.BleDevice

enum class SortType { FIRST_SEEN, RSSI }

sealed interface ScanStatus {
    data object Idle : ScanStatus
    data object Scanning : ScanStatus
    data class Error(val reason: ScanFailure) : ScanStatus
}

data class ScanFilter(
    val sortType: SortType = SortType.FIRST_SEEN,
    val namedOnly: Boolean = false,
    val query: String = "",
    val sortLocked: Boolean = false,
)

data class ScanUiState(
    val status: ScanStatus = ScanStatus.Idle,
    val devices: List<BleDevice> = emptyList(),
    val filter: ScanFilter = ScanFilter(),
) {
    val isScanning: Boolean get() = status == ScanStatus.Scanning
}
