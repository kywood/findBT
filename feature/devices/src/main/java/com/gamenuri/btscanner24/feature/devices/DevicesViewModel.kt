package com.gamenuri.btscanner24.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gamenuri.btscanner24.core.ble.BleDependencies
import com.gamenuri.btscanner24.core.ble.BleScanner
import com.gamenuri.btscanner24.core.ble.BondedDevices
import com.gamenuri.btscanner24.core.ble.ConnectedDevices
import com.gamenuri.btscanner24.core.ble.DeviceRepository
import com.gamenuri.btscanner24.core.ble.ScanEvent
import com.gamenuri.btscanner24.core.ble.ScanFailure
import com.gamenuri.btscanner24.core.ble.scanContinuously
import com.gamenuri.btscanner24.core.ble.watch
import com.gamenuri.btscanner24.core.data.DataDependenciesProvider
import com.gamenuri.btscanner24.core.data.HiddenDevicesRepository
import com.gamenuri.btscanner24.core.data.LastSeenRepository
import com.gamenuri.btscanner24.core.data.SavedDevicesRepository
import com.gamenuri.btscanner24.core.model.PairedDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * My Devices: paired devices, marked nearby when the scan hears them. Scans only while the tab is
 * visible, like the Nearby tab.
 */
@OptIn(FlowPreview::class)
class DevicesViewModel(
    private val scanner: BleScanner,
    private val repository: DeviceRepository,
    private val bondedDevices: BondedDevices,
    private val connectedDevices: ConnectedDevices,
    private val hiddenDevices: HiddenDevicesRepository,
    private val savedDevices: SavedDevicesRepository,
    private val lastSeen: LastSeenRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val paired = MutableStateFlow<List<PairedDevice>>(emptyList())
    private val hasPermission = MutableStateFlow(bondedDevices.hasPermission())
    private val scanFailure = MutableStateFlow<ScanFailure?>(null)
    private val connected = MutableStateFlow<Set<String>>(emptySet())
    private var scanJob: Job? = null
    private var connectedJob: Job? = null

    private val listed = combine(
        paired,
        repository.devices.sample(UI_REFRESH_MS),
        hiddenDevices.hidden,
        combine(hasPermission, scanFailure, connected, ::Triple),
        savedDevices.saved,
    ) { pairedList, seen, hidden, (permitted, failure, linked), saved ->
        MyDevices.build(pairedList, seen.values, hidden, clock(), saved, linked) { it.distanceMeters }
            .copy(hasPermission = permitted, scanFailure = failure)
    }

    val uiState: StateFlow<MyDevicesUiState> = combine(listed, lastSeen.lastSeen) { state, records ->
        state.withLastSeen(records)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyDevicesUiState())

    /** Called when the tab becomes visible: re-read paired devices (the user may have paired one) and scan. */
    fun onScreenStarted() {
        hasPermission.value = bondedDevices.hasPermission()
        paired.value = bondedDevices.list()
        // Connected devices do not need the scan (or Location): they show even when it cannot run (#119).
        if (connectedJob?.isActive != true) {
            connectedJob = viewModelScope.launch(Dispatchers.Default) {
                connectedDevices.watch(repository, { paired.value }) { connected.value = it }
            }
        }
        if (scanJob?.isActive == true || !scanner.hasScanPermission()) return
        scanFailure.value = null
        scanJob = viewModelScope.launch(Dispatchers.Default) {
            // Waits out Android's 5-starts-per-30-s budget shared with the Nearby tab (#62) and starts
            // again when the scan dies quietly, so a device nearby keeps its signal (#116).
            scanner.scanContinuously().collect { event ->
                when (event) {
                    is ScanEvent.Result -> {
                        if (scanFailure.value != null) scanFailure.value = null
                        repository.onScanResult(event.result)
                    }
                    is ScanEvent.Failed -> scanFailure.value = event.reason
                }
            }
        }
    }

    fun onScreenStopped() {
        scanJob?.cancel()
        scanJob = null
        connectedJob?.cancel()
        connectedJob = null
    }

    fun hide(item: MyDeviceItem) {
        viewModelScope.launch { hiddenDevices.hide(item.paired.address) }
    }

    /** Takes a saved device off the list for good (#108); paired ones are hidden instead. */
    fun remove(item: MyDeviceItem) {
        viewModelScope.launch {
            savedDevices.remove(item.paired.address)
            // Its last place goes with it: nothing is kept about a device the user took off the list.
            lastSeen.remove(item.paired.address)
        }
    }

    override fun onCleared() {
        onScreenStopped()
    }

    companion object {
        private const val UI_REFRESH_MS = 1_000L

        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val ble = (app as BleDependencies.Provider).bleDependencies
                val data = app as DataDependenciesProvider
                DevicesViewModel(
                    ble.scanner,
                    ble.repository,
                    ble.bondedDevices,
                    ble.connectedDevices,
                    data.hiddenDevices,
                    data.savedDevices,
                    data.lastSeen,
                )
            }
        }
    }
}
