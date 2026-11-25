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
 * Holds every device seen in the current session, keyed by MAC address. A device that comes back
 * under a new random address replaces its old row instead of adding one (see [AddressRotation]).
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
            // The same device under a new random address (#115)? Only a young row is checked, and for
            // a few seconds, because the name often arrives a packet or two after the address.
            val old = if (now - mapped.firstSeen <= ROTATION_CHECK_MS) {
                AddressRotation.predecessor(mapped, _devices.value.values, now)
            } else {
                null
            }
            if (old == null) {
                smooth(mapped, now).also { _devices.value += (address to it) }
            } else {
                // Carry on as the same row: its history and its smoothing move to the new address.
                estimators.remove(old.address)?.let { estimators[address] = it }
                smooth(mapped.copy(firstSeen = old.firstSeen), now)
                    .also { _devices.value = _devices.value - old.address + (address to it) }
            }
        }
        _sightings.tryEmit(device)
    }

    private fun smooth(device: BleDevice, now: Long): BleDevice {
        val reading = estimators.getOrPut(device.address) { ProximityEstimator() }
            .add(device.rssi, now, device.txPower)
        return device.copy(smoothedRssi = reading.smoothedRssi, distanceMeters = reading.distanceMeters)
    }

    /** Replaces everything with fixed devices: the debug demo mode (#47) and tests. */
    fun replaceAll(devices: Collection<BleDevice>) {
        synchronized(lock) {
            estimators.clear()
            _devices.value = devices.associateBy { it.address }
        }
    }

    fun clear() {
        synchronized(lock) {
            estimators.clear()
            _devices.value = emptyMap()
        }
    }

    private companion object {
        const val SIGHTING_BUFFER = 64
        const val ROTATION_CHECK_MS = 10_000L
    }
}
