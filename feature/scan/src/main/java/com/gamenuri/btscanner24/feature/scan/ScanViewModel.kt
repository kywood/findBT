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
import com.gamenuri.btscanner24.core.data.DataDependenciesProvider
import com.gamenuri.btscanner24.core.data.ListPreferencesRepository
import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.CategoryGroup
import com.gamenuri.btscanner24.core.model.DeviceSort
import com.gamenuri.btscanner24.core.model.ListPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScanViewModel(
    private val scanner: BleScanner,
    private val repository: DeviceRepository,
    private val listPreferences: ListPreferencesRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val status = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    private val query = MutableStateFlow("")
    private val reorderRequests = MutableStateFlow(0)

    private val preferences: StateFlow<ListPreferences> = listPreferences.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, ListPreferences())

    private var scanJob: Job? = null

    /** True when the scan was paused because the screen went to the background. */
    private var resumeOnStart = false

    private val ordering = OrderingState()

    @OptIn(FlowPreview::class)
    private val throttledDevices = repository.devices
        // Scan callbacks can arrive dozens of times per second; redraw at most twice a second.
        .sample(UI_REFRESH_MS)
        .onStart { emit(repository.devices.value) }

    /** Re-evaluates "gone" devices even when no packets arrive. */
    private val ticker = flow {
        while (true) {
            emit(clock())
            delay(GONE_CHECK_MS)
        }
    }

    private val listInputs = combine(preferences, query, reorderRequests) { prefs, q, token -> Triple(prefs, q, token) }

    val uiState: StateFlow<ScanUiState> =
        combine(status, throttledDevices, listInputs, ticker) { status, devices, (prefs, q, token), _ ->
            val now = clock()
            val (items, reorderAvailable) = ordering.update(devices.values, prefs, q, token, now)
            ScanUiState(
                status = status,
                items = items,
                preferences = prefs,
                query = q,
                reorderAvailable = reorderAvailable,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanUiState())

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
        // Parsing and the company-table lookup are too heavy for the main thread (cold-start jank, #34).
        scanJob = viewModelScope.launch(Dispatchers.Default) {
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
        query.value = ""
        refreshOrder()
    }

    /** The "Refresh order" button: apply the chosen sort to the rows on screen. */
    fun refreshOrder() {
        reorderRequests.value++
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSort(sort: DeviceSort) = updatePreferences { it.copy(sort = sort) }
    fun toggleNamedOnly() = updatePreferences { it.copy(namedOnly = !it.namedOnly) }
    fun toggleHideGone() = updatePreferences { it.copy(hideGone = !it.hideGone) }
    fun toggleGroup(group: CategoryGroup) = updatePreferences {
        it.copy(groups = if (group in it.groups) it.groups - group else it.groups + group)
    }

    private fun updatePreferences(transform: (ListPreferences) -> ListPreferences) {
        viewModelScope.launch { listPreferences.update(transform) }
    }

    override fun onCleared() {
        stopScan()
    }

    companion object {
        private const val UI_REFRESH_MS = 500L
        private const val GONE_CHECK_MS = 5_000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]
                val ble = (app as BleDependencies.Provider).bleDependencies
                val data = app as DataDependenciesProvider
                ScanViewModel(ble.scanner, ble.repository, data.listPreferences)
            }
        }
    }
}

/**
 * Remembers what is on screen between emissions. A full sort happens on the first list, when
 * sort/filter/search change, and when the user taps "Refresh order"; otherwise rows keep their place.
 * Right after a full sort the list keeps sorting for [SETTLE_MS] while the first burst of
 * advertisements arrives, so a fresh scan does not show every device as "new".
 */
internal class OrderingState {
    private var displayedOrder: List<String> = emptyList()
    private var settleUntil: Long = 0
    private var sortedAddresses: Set<String> = emptySet()
    private var lastPrefs: ListPreferences? = null
    private var lastQuery: String? = null
    private var lastToken: Int? = null

    fun update(
        devices: Collection<BleDevice>,
        prefs: ListPreferences,
        query: String,
        token: Int,
        now: Long,
    ): Pair<List<DeviceListItem>, Boolean> {
        val filtered = DeviceListOrdering.filter(devices, prefs, query, now)
        val sorted = DeviceListOrdering.sort(filtered, prefs.sort)
        val explicit = displayedOrder.isEmpty() || prefs != lastPrefs || query != lastQuery || token != lastToken
        if (explicit && filtered.isNotEmpty()) settleUntil = now + SETTLE_MS
        val fullSort = explicit || now < settleUntil
        lastPrefs = prefs
        lastQuery = query
        lastToken = token

        val ordered = if (fullSort) sorted else DeviceListOrdering.keepOrder(displayedOrder, filtered)
        if (fullSort) sortedAddresses = sorted.mapTo(HashSet()) { it.address }
        displayedOrder = ordered.map { it.address }

        val items = ordered.map { device ->
            DeviceListItem(
                device = device,
                isNew = device.address !in sortedAddresses,
                isGone = DeviceListOrdering.isGone(device, now),
                distanceMeters = device.distanceMeters,
            )
        }
        val reorderAvailable = !fullSort && sorted.map { it.address } != displayedOrder
        return items to reorderAvailable
    }

    companion object {
        const val SETTLE_MS = 3_000L
    }
}
