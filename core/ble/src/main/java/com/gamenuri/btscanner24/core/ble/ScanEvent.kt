package com.gamenuri.btscanner24.core.ble

import android.bluetooth.le.ScanResult

/** Raw events emitted by [BleScanner]. */
sealed interface ScanEvent {
    data class Result(val result: ScanResult) : ScanEvent
    data class Failed(val reason: ScanFailure) : ScanEvent
}

enum class ScanFailure {
    /** Device has no BLE hardware or the adapter is unavailable. */
    NOT_SUPPORTED,
    /** Bluetooth is turned off. */
    BLUETOOTH_OFF,
    /** Required runtime permission is missing. */
    PERMISSION_MISSING,
    /** The system rejected the scan because it was started too often (Android limits ~5 starts / 30 s). */
    TOO_FREQUENT,
    /** Any other error reported by ScanCallback.onScanFailed. */
    INTERNAL,
}
