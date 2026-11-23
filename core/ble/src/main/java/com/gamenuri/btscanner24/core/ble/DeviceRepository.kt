package com.gamenuri.btscanner24.core.ble

import com.gamenuri.btscanner24.core.model.BleDevice
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * Holds every device seen in the current session, keyed by MAC address.
 * Updates go through [MutableStateFlow.update], so callbacks from any thread are safe.
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

    fun onScanResult(result: android.bluetooth.le.ScanResult) {
        val now = clock()
        val address = result.device.address
        val updated = _devices.updateAndGet { current ->
            current + (address to ScanResultMapper.map(result, current[address], now, companies))
        }
        updated[address]?.let(_sightings::tryEmit)
    }

    fun clear() {
        _devices.value = emptyMap()
    }

    private companion object {
        const val SIGHTING_BUFFER = 64
    }
}
