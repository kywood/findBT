package com.gamenuri.btscanner24.core.ble

/**
 * Shared BLE objects for the whole app. One instance lives in the Application so that every
 * feature (scan, finder, ...) sees the same scanner and device list.
 */
class BleDependencies(
    val scanner: BleScanner,
    val repository: DeviceRepository,
    val bondedDevices: BondedDevices,
    val connectedDevices: ConnectedDevices,
) {
    /** Implemented by the Application; feature ViewModel factories read dependencies through it. */
    interface Provider {
        val bleDependencies: BleDependencies
    }
}
