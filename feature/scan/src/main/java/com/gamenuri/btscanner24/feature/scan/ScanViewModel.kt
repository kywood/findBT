package com.gamenuri.btscanner24.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gamenuri.btscanner24.core.ble.BleDependencies
import com.gamenuri.btscanner24.core.ble.BondedDevices
import com.gamenuri.btscanner24.core.ble.DeviceRepository
import com.gamenuri.btscanner24.core.ble.ScanFailure
import com.gamenuri.btscanner24.core.ble.ScanSession
import com.gamenuri.btscanner24.core.data.DataDependenciesProvider
import com.gamenuri.btscanner24.core.data.ListPreferencesRepository
import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.DeviceFilter
import com.gamenuri.btscanner24.core.model.DeviceNaming
import com.gamenuri.btscanner24.core.model.DeviceSort
import com.gamenuri.btscanner24.core.model.ListPreferences
import kotlinx.coroutines.FlowPreview
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
    private val scan: ScanSession,
    private val repository: DeviceRepository,
    private val listPreferences: ListPreferencesRepository,
    private val bondedDevices: BondedDevices? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val reorderRequests = MutableStateFlow(0)

    /** Sorts asked for by the timer or by scrolling to the top, not by a tap: personal rows only (#143). */
    private val autoReorderRequests = MutableStateFlow(0)

    /** Paired address -> the phone's name for it, re-read whenever the screen starts (#46). */
    private val pairedAliases = MutableStateFlow<Map<String, String>>(emptyMap())

    private val preferences: StateFlow<ListPreferences> = listPreferences.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, ListPreferences())

    /** This screen's hold on the shared scan (#177); null while it does not want one. */
    private var scanHold: AutoCloseable? = null
    private val holding = MutableStateFlow(false)

    /** The failure the user closed the message for; it stays closed until the scan is asked for again. */
    private val dismissedFailure = MutableStateFlow<ScanFailure?>(null)

    /** Scanning while this screen holds the scan, unless the scan says why it cannot run. */
    private val status = combine(scan.failure, holding, dismissedFailure) { failure, held, dismissed ->
        when {
            !held || (failure != null && failure == dismissed) -> ScanStatus.Idle
            failure == null -> ScanStatus.Scanning
            else -> ScanStatus.Error(failure)
        }
    }

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

    private val reorderTokens = combine(reorderRequests, autoReorderRequests) { asked, auto -> asked to auto }

    private val listInputs = combine(preferences, query, reorderTokens, pairedAliases) { prefs, q, tokens, aliases ->
        ListInputs(prefs, q, tokens.first, tokens.second, aliases)
    }

    private val scanState = combine(status, paused) { status, paused -> status to paused }

    val uiState: StateFlow<ScanUiState> =
        combine(scanState, throttledDevices, listInputs, ticker) { (status, paused), devices, inputs, _ ->
            val now = clock()
            val (prefs, q, token, autoToken, aliases) = inputs
            val list = ordering.update(devices.values, prefs, q, token, now, aliases, autoToken)
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
        paused.value = false
        dismissedFailure.value = null
        // The shared scan waits out Android's 5 starts per 30 s instead of showing an error (#62) and
        // starts again when it dies quietly (#116). After a failure this is the retry.
        if (scanHold == null) {
            scanHold = scan.acquire()
            holding.value = true
        } else {
            scan.retry()
        }
    }

    fun stopScan() {
        scanHold?.close()
        scanHold = null
        holding.value = false
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
        if (scan.hasPermission) startScan()
    }

    private fun refreshPaired() {
        val bonded = bondedDevices ?: return
        pairedAliases.value = bonded.list()
            .mapNotNull { paired -> paired.name?.takeIf { it.isNotBlank() }?.let { paired.address to it } }
            .toMap()
    }

    fun dismissError() {
        dismissedFailure.value = scan.failure.value
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

    /**
     * The same without a tap (#87: after a few seconds, or on scrolling back to the top). It sorts the
     * personal rows only; the other devices keep their places until the user asks (#143).
     */
    fun autoRefreshOrder() {
        autoReorderRequests.value++
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSort(sort: DeviceSort) = updatePreferences { it.copy(sort = sort) }

    /** List or 3D map on the Nearby tab (#86); remembered like the sort. */
    fun setShowMap(show: Boolean) = updatePreferences { it.copy(showMap = show) }

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
                ScanViewModel(ble.scanSession, ble.repository, data.listPreferences, ble.bondedDevices)
            }
        }
    }
}

internal data class OrderedList(val items: List<DeviceListItem>, val reorderAvailable: Boolean, val hiddenOther: Int)

private data class ListInputs(
    val prefs: ListPreferences,
    val query: String,
    val token: Int,
    val autoToken: Int,
    val aliases: Map<String, String>,
)

/**
 * Remembers what is on screen between emissions. A full sort happens on the first list, when
 * sort/filter/search change, and when the user taps "Refresh order"; otherwise rows keep their place.
 * Right after a full sort the list keeps sorting for [SETTLE_MS] while the first burst of
 * advertisements arrives, so a fresh scan does not show every device as "new".
 *
 * The "Other devices" section (#82) is long and its signals jitter, so its order would never agree
 * with the sort for long: it is sorted only on those explicit occasions, never by the automatic
 * re-sort, and it does not raise the "new order" chip (#143).
 */
internal class OrderingState {
    private var displayedOrder: List<String> = emptyList()
    private var settleUntil: Long = 0
    private var otherSettleUntil: Long = 0
    private var sortedPersonal: Set<String> = emptySet()
    private var sortedOther: Set<String> = emptySet()
    private var lastPrefs: ListPreferences? = null
    private var lastQuery: String? = null
    private var lastToken: Int? = null
    private var lastAutoToken: Int? = null

    fun update(
        devices: Collection<BleDevice>,
        prefs: ListPreferences,
        query: String,
        token: Int,
        now: Long,
        pairedAliases: Map<String, String> = emptyMap(),
        autoToken: Int = 0,
    ): OrderedList {
        val split = DeviceListOrdering.filter(devices, prefs.filter, query, now, paired = pairedAliases.keys)
        val otherAddresses = split.other.mapTo(HashSet()) { it.address }
        val explicit = displayedOrder.isEmpty() || prefs != lastPrefs || query != lastQuery || token != lastToken
        val auto = lastAutoToken != null && autoToken != lastAutoToken
        if (explicit && split.all.isNotEmpty()) {
            settleUntil = now + SETTLE_MS
            otherSettleUntil = now + SETTLE_MS
        } else if (auto && split.personal.isNotEmpty()) {
            settleUntil = now + SETTLE_MS
        }
        val sortPersonal = explicit || auto || now < settleUntil
        val sortOther = explicit || now < otherSettleUntil
        lastPrefs = prefs
        lastQuery = query
        lastToken = token
        lastAutoToken = autoToken

        // Personal devices first, then the "Other devices" section, each in the chosen sort or as it was.
        val sortedPersonalRows = DeviceListOrdering.sort(split.personal, prefs.sort)
        val personal = if (sortPersonal) {
            sortedPersonalRows
        } else {
            DeviceListOrdering.keepOrder(displayedOrder, split.personal)
        }
        val other = if (sortOther) {
            DeviceListOrdering.sort(split.other, prefs.sort)
        } else {
            DeviceListOrdering.keepOrder(displayedOrder, split.other)
        }
        val ordered = personal + other
        if (sortPersonal) sortedPersonal = personal.mapTo(HashSet()) { it.address }
        if (sortOther) sortedOther = other.mapTo(HashSet()) { it.address }
        displayedOrder = ordered.map { it.address }

        val labels = ordered.map { DeviceNaming.label(it, pairedAlias = pairedAliases[it.address]) }
        val duplicates = DeviceNaming.duplicateNames(labels)
        val items = ordered.mapIndexed { index, device ->
            DeviceListItem(
                device = device,
                isNew = device.address !in sortedPersonal && device.address !in sortedOther,
                isGone = DeviceListOrdering.isGone(device, now),
                distanceMeters = device.distanceMeters,
                label = labels[index],
                showSuffix = DeviceNaming.isDuplicate(labels[index], duplicates),
                isPaired = device.address in pairedAliases,
                isOther = device.address in otherAddresses,
            )
        }
        val reorderAvailable = !sortPersonal && sortedPersonalRows.map { it.address } != personal.map { it.address }
        return OrderedList(items, reorderAvailable, split.hiddenOther)
    }

    companion object {
        const val SETTLE_MS = 3_000L
    }
}
