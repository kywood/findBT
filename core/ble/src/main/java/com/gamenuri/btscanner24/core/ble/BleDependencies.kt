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
    /** The find tone through connected earbuds (#153); null leaves the feature out of the build. */
    val mediaFindTone: MediaFindTone? = null,
    /** The scan the screens share (#177): they hold it instead of scanning themselves. */
    val scanSession: ScanSession = SharedScanSession(scanner, repository),
    /** Directions the find screen measured, for the Nearby map (#174). */
    val measuredBearings: MeasuredBearings = MeasuredBearings(),
) {
    /** Implemented by the Application; feature ViewModel factories read dependencies through it. */
    interface Provider {
        val bleDependencies: BleDependencies
    }
}
