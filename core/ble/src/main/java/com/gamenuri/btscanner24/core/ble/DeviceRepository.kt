package com.gamenuri.btscanner24.core.ble

import com.gamenuri.btscanner24.core.ble.proximity.ProximityEstimator
import com.gamenuri.btscanner24.core.model.BleDevice
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds every device seen in the current session, keyed by MAC address.
 * Updates are serialized on one lock, so callbacks from any thread are safe.
 * Each device keeps its own [ProximityEstimator], so list rows, My devices and find mode all show
 * the same smoothed RSSI and distance.
 */
class DeviceRepository(
    private val companies: CompanyDirectory,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _devices = MutableStateFlow<Map<String, BleDevice>>(emptyMap())
    val devices: StateFlow<Map<String, BleDevice>> = _devices.asStateFlow()

    private val _sightings = MutableSharedFlow<BleDevice>(
        extraBufferCapacity = SIGHTING_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Every advertisement as it arrives. [devices] conflates updates, which is fine for a list but
     * drops RSSI samples; find mode needs each one for smoothing.
     */
    val sightings: SharedFlow<BleDevice> = _sightings.asSharedFlow()

    /** Estimators are not thread safe and a CAS retry would feed a sample twice, so updates share one lock. */
    private val lock = Any()
    private val estimators = HashMap<String, ProximityEstimator>()

    fun onScanResult(result: android.bluetooth.le.ScanResult) {
        val now = clock()
        val address = result.device.address
        val device = synchronized(lock) {
            val mapped = ScanResultMapper.map(result, _devices.value[address], now, companies)
            smooth(mapped, now).also { _devices.value += (address to it) }
        }
        _sightings.tryEmit(device)
    }

    private fun smooth(device: BleDevice, now: Long): BleDevice {
        val reading = estimators.getOrPut(device.address) { ProximityEstimator() }
            .add(device.rssi, now, device.txPower)
        return device.copy(smoothedRssi = reading.smoothedRssi, distanceMeters = reading.distanceMeters)
    }

    fun clear() {
        synchronized(lock) {
            estimators.clear()
            _devices.value = emptyMap()
        }
    }

    private companion object {
        const val SIGHTING_BUFFER = 64
    }
}
