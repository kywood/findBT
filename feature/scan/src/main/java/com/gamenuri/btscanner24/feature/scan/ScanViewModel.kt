package com.gamenuri.btscanner24.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gamenuri.btscanner24.core.ble.BleDependencies
import com.gamenuri.btscanner24.core.ble.BleScanner
import com.gamenuri.btscanner24.core.ble.DeviceRepository
import com.gamenuri.btscanner24.core.ble.ScanEvent
import com.gamenuri.btscanner24.core.ble.ScanFailure
import com.gamenuri.btscanner24.core.model.BleDevice
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScanViewModel(
    private val scanner: BleScanner,
    private val repository: DeviceRepository,
) : ViewModel() {

    private val status = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    private val filter = MutableStateFlow(ScanFilter())

    private var scanJob: Job? = null
    /** True when the scan was paused because the screen went to the background. */
    private var resumeOnStart = false
    /** Last displayed order, used when the sort is locked. */
    private var displayedOrder: List<String> = emptyList()

    @OptIn(FlowPreview::class)
    private val throttledDevices = repository.devices
        // Scan callbacks can arrive dozens of times per second; redraw at most twice a second.
        .sample(UI_REFRESH_MS)
        .onStart { emit(repository.devices.value) }

    val uiState: StateFlow<ScanUiState> =
        combine(status, throttledDevices, filter) { status, devices, filter ->
            ScanUiState(status = status, devices = order(devices.values, filter), filter = filter)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanUiState())

    private fun order(devices: Collection<BleDevice>, filter: ScanFilter): List<BleDevice> {
        val filtered = DeviceListOrdering.filter(devices, filter)
        val ordered = if (filter.sortLocked) {
            DeviceListOrdering.keepOrder(displayedOrder, filtered)
        } else {
            DeviceListOrdering.sort(filtered, filter.sortType)
        }
        displayedOrder = ordered.map { it.address }
        return ordered
    }

    fun toggleScan() {
        if (scanJob?.isActive == true) stopScan() else startScan()
    }

    fun startScan() {
        if (scanJob?.isActive == true) return
        if (scanner.startCooldownMillis() > 0) {
            status.value = ScanStatus.Error(ScanFailure.TOO_FREQUENT)
            return
        }
        status.value = ScanStatus.Scanning
        scanJob = viewModelScope.launch {
            scanner.scan().collect { event ->
                when (event) {
                    is ScanEvent.Result -> repository.onScanResult(event.result)
                    is ScanEvent.Failed -> status.value = ScanStatus.Error(event.reason)
                }
            }
            // Flow completed on its own (failure); keep the error status if one was set.
            if (status.value == ScanStatus.Scanning) status.value = ScanStatus.Idle
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        status.value = ScanStatus.Idle
    }

    /** Called when the screen leaves the foreground: never scan in the background. */
    fun onScreenStopped() {
        resumeOnStart = scanJob?.isActive == true
        if (resumeOnStart) stopScan()
    }

    fun onScreenStarted() {
        if (resumeOnStart) {
            resumeOnStart = false
            startScan()
        }
    }

    fun dismissError() {
        if (status.value is ScanStatus.Error) status.value = ScanStatus.Idle
    }

    fun reset() {
        stopScan()
        repository.clear()
        displayedOrder = emptyList()
        filter.update { it.copy(query = "") }
    }

    fun setQuery(query: String) = filter.update { it.copy(query = query) }
    fun setSortType(sortType: SortType) = filter.update { it.copy(sortType = sortType, sortLocked = false) }
    fun toggleNamedOnly() = filter.update { it.copy(namedOnly = !it.namedOnly) }
    fun toggleSortLock() = filter.update { it.copy(sortLocked = !it.sortLocked) }

    override fun onCleared() {
        stopScan()
    }

    companion object {
        private const val UI_REFRESH_MS = 500L

        val Factory = viewModelFactory {
            initializer {
                val deps = (this[APPLICATION_KEY] as BleDependencies.Provider).bleDependencies
                ScanViewModel(deps.scanner, deps.repository)
            }
        }
    }
}
