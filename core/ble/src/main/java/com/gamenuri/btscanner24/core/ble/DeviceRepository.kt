package com.gamenuri.btscanner24.core.ble

import com.gamenuri.btscanner24.core.model.BleDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

    fun onScanResult(result: android.bluetooth.le.ScanResult) {
        val now = clock()
        _devices.update { current ->
            val address = result.device.address
            current + (address to ScanResultMapper.map(result, current[address], now, companies))
        }
    }

    fun clear() {
        _devices.value = emptyMap()
    }
}
