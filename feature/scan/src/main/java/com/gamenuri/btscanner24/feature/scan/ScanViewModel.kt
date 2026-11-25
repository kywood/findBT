package com.gamenuri.btscanner24.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gamenuri.btscanner24.core.ble.BleDependencies
import com.gamenuri.btscanner24.core.ble.BleScanner
import com.gamenuri.btscanner24.core.ble.BondedDevices
import com.gamenuri.btscanner24.core.ble.DeviceRepository
import com.gamenuri.btscanner24.core.ble.ScanEvent
import com.gamenuri.btscanner24.core.data.DataDependenciesProvider
import com.gamenuri.btscanner24.core.data.ListPreferencesRepository
import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.DeviceFilter
import com.gamenuri.btscanner24.core.model.DeviceNaming
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
    private val bondedDevices: BondedDevices? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val status = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    private val query = MutableStateFlow("")
    private val reorderRequests = MutableStateFlow(0)

    /** Paired address -> the phone's name for it, re-read whenever the screen starts (#46). */
    private val pairedAliases = MutableStateFlow<Map<String, String>>(emptyMap())

    private val preferences: StateFlow<ListPreferences> = listPreferences.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, ListPreferences())

    private var scanJob: Job? = null

    private val paused = MutableStateFlow(false)

    private val ordering = OrderingState()

    /** The devices behind the last list, for the filter screen's live count. */
    @Volatile private var latest: Collection<BleDevice> = emptyList()

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

    private val listInputs = combine(preferences, query, reorderRequests, pairedAliases) { prefs, q, token, aliases ->
        ListInputs(prefs, q, token, aliases)
    }

    private val scanState = combine(status, paused) { status, paused -> status to paused }

    val uiState: StateFlow<ScanUiState> =
        combine(scanState, throttledDevices, listInputs, ticker) { (status, paused), devices, inputs, _ ->
            val now = clock()
            val (prefs, q, token, aliases) = inputs
            val list = ordering.update(devices.values, prefs, q, token, now, aliases)
            latest = devices.values
            ScanUiState(
                status = status,
                items = list.items,
                preferences = prefs,
                query = q,
                reorderAvailable = list.reorderAvailable,
                hiddenOtherCount = list.hiddenOther,
                isPaused = paused,
                totalCount = devices.size,
                census = DeviceListOrdering.census(devices.values, aliases.keys),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanUiState())

    fun startScan() {
        if (scanJob?.isActive == true) return
        paused.value = false
        status.value = ScanStatus.Scanning
        // Parsing and the company-table lookup are too heavy for the main thread (cold-start jank, #34).
        scanJob = viewModelScope.launch(Dispatchers.Default) {
            // Android allows 5 scan starts per 30 s. Switching tabs quickly can hit that; wait it out
            // instead of showing an error, since scanning is automatic now (#62).
            val cooldown = scanner.startCooldownMillis()
            if (cooldown > 0) delay(cooldown)
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

    /** Top-bar pause; the scan stays off until [resume] or until the user leaves and comes back. */
    fun pause() {
        stopScan()
        paused.value = true
    }

    fun resume() = startScan()

    /** Called when the tab leaves the screen or the app goes to the background: never scan then. */
    fun onScreenStopped() {
        stopScan()
        paused.value = false
    }

    /** Scanning is automatic while the Nearby tab is visible (#62). */
    fun onScreenStarted() {
        refreshPaired()
        if (scanner.hasScanPermission()) startScan()
    }

    private fun refreshPaired() {
        val bonded = bondedDevices ?: return
        pairedAliases.value = bonded.list()
            .mapNotNull { paired -> paired.name?.takeIf { it.isNotBlank() }?.let { paired.address to it } }
            .toMap()
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

    /** "N other devices hidden · Show" at the end of the list (#82): same as turning the switch on. */
    fun showOther() = updateFilter { it.copy(showOther = true) }

    /** "Other devices · N · Close" over that section (#142): the switch off again. */
    fun hideOther() = updateFilter { it.copy(showOther = false) }

    /** The filter screen's "Show N devices" (#85): applies the whole edited filter at once. */
    fun applyFilter(filter: DeviceFilter) = updateFilter { filter }

    /** How many rows [filter] would show right now, with the current search. */
    fun resultCount(filter: DeviceFilter): Int =
        DeviceListOrdering.count(latest, filter, query.value, clock(), pairedAliases.value.keys)

    private fun updatePreferences(transform: (ListPreferences) -> ListPreferences) {
        viewModelScope.launch { listPreferences.update(transform) }
    }

    private fun updateFilter(transform: (DeviceFilter) -> DeviceFilter) {
        updatePreferences { it.copy(filter = transform(it.filter)) }
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
                ScanViewModel(ble.scanner, ble.repository, data.listPreferences, ble.bondedDevices)
            }
        }
    }
}

internal data class OrderedList(val items: List<DeviceListItem>, val reorderAvailable: Boolean, val hiddenOther: Int)

private data class ListInputs(
    val prefs: ListPreferences,
    val query: String,
    val token: Int,
    val aliases: Map<String, String>,
)

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
        pairedAliases: Map<String, String> = emptyMap(),
    ): OrderedList {
        val split = DeviceListOrdering.filter(devices, prefs.filter, query, now, paired = pairedAliases.keys)
        val filtered = split.all
        // Personal devices first, then the "Other devices" section (#82), each in the chosen sort.
        val sorted = DeviceListOrdering.sort(split.personal, prefs.sort) +
            DeviceListOrdering.sort(split.other, prefs.sort)
        val otherAddresses = split.other.mapTo(HashSet()) { it.address }
        val explicit = displayedOrder.isEmpty() || prefs != lastPrefs || query != lastQuery || token != lastToken
        if (explicit && filtered.isNotEmpty()) settleUntil = now + SETTLE_MS
        val fullSort = explicit || now < settleUntil
        lastPrefs = prefs
        lastQuery = query
        lastToken = token

        val kept = if (fullSort) sorted else DeviceListOrdering.keepOrder(displayedOrder, filtered)
        // Keep-order may interleave a device that switched section; the sections themselves stay apart.
        val ordered = kept.filter { it.address !in otherAddresses } + kept.filter { it.address in otherAddresses }
        if (fullSort) sortedAddresses = sorted.mapTo(HashSet()) { it.address }
        displayedOrder = ordered.map { it.address }

        val labels = ordered.map { DeviceNaming.label(it, pairedAlias = pairedAliases[it.address]) }
        val duplicates = DeviceNaming.duplicateNames(labels)
        val items = ordered.mapIndexed { index, device ->
            DeviceListItem(
                device = device,
                isNew = device.address !in sortedAddresses,
                isGone = DeviceListOrdering.isGone(device, now),
                distanceMeters = device.distanceMeters,
                label = labels[index],
                showSuffix = DeviceNaming.isDuplicate(labels[index], duplicates),
                isPaired = device.address in pairedAliases,
                isOther = device.address in otherAddresses,
            )
        }
        val reorderAvailable = !fullSort && sorted.map { it.address } != displayedOrder
        return OrderedList(items, reorderAvailable, split.hiddenOther)
    }

    companion object {
        const val SETTLE_MS = 3_000L
    }
}
