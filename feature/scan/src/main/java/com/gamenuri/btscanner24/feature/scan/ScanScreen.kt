package com.gamenuri.btscanner24.feature.scan

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamenuri.btscanner24.core.ble.BleScanner
import com.gamenuri.btscanner24.core.ble.ScanFailure
import com.gamenuri.btscanner24.core.designsystem.component.IllustratedMessage
import com.gamenuri.btscanner24.core.designsystem.component.StatusBanner
import com.gamenuri.btscanner24.core.designsystem.icon.NearoIcons
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.DeviceFilter
import com.gamenuri.btscanner24.core.model.DeviceSort
import com.gamenuri.btscanner24.core.ui.DeviceCard
import com.gamenuri.btscanner24.core.ui.R as UiR
import com.gamenuri.btscanner24.core.ui.permission.rememberBlePermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Entry point: owns the ViewModel, permissions and system intents. Everything visual is in [ScanContent]. */
@Composable
fun ScanScreen(
    title: String,
    onDeviceClick: (BleDevice) -> Unit = {},
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    viewModel: ScanViewModel = viewModel(factory = ScanViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Scans by itself while this tab is visible; stops on other tabs and in the background (#62).
    LifecycleStartEffect(viewModel) {
        viewModel.onScreenStarted()
        onStopOrDispose { viewModel.onScreenStopped() }
    }

    val permission = rememberBlePermissionState(
        permissions = BleScanner.requiredPermissions(),
        onGranted = viewModel::startScan,
    )
    val enableBluetooth = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.startScan() }
    // Coming back from the system Location screen: try again (it is checked before scanning).
    val enableLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.startScan() }

    ScanContent(
        modifier = modifier,
        title = title,
        state = state,
        permissionBanner = when {
            permission.isGranted -> null
            permission.isPermanentlyDenied -> PermissionBanner.OPEN_SETTINGS
            else -> PermissionBanner.ASK_AGAIN
        },
        actions = ScanActions(
            onPause = viewModel::pause,
            onResume = { if (permission.isGranted) viewModel.resume() else permission.request() },
            onPermissionAction = permission::request,
            onEnableBluetooth = {
                viewModel.dismissError()
                enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            },
            onEnableLocation = {
                viewModel.dismissError()
                enableLocation.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            },
            onRetry = viewModel::startScan,
            onDismissError = viewModel::dismissError,
            onReset = viewModel::reset,
            onQueryChange = viewModel::setQuery,
            onSortChange = viewModel::setSort,
            onApplyFilter = viewModel::applyFilter,
            onShowOther = viewModel::showOther,
            onHideOther = viewModel::hideOther,
            countFor = viewModel::resultCount,
            onRefreshOrder = viewModel::refreshOrder,
            onDeviceClick = onDeviceClick,
            onOpenSettings = onOpenSettings,
        ),
    )
}

enum class PermissionBanner { ASK_AGAIN, OPEN_SETTINGS }

/** All user events of the scan screen, so [ScanContent] stays stateless and previewable. */
data class ScanActions(
    val onPause: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onPermissionAction: () -> Unit = {},
    val onEnableBluetooth: () -> Unit = {},
    val onEnableLocation: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onSortChange: (DeviceSort) -> Unit = {},
    /** The filter screen's "Show N devices" (#85). */
    val onApplyFilter: (DeviceFilter) -> Unit = {},
    /** "N other devices hidden · Show" at the end of the list (#82). */
    val onShowOther: () -> Unit = {},
    /** "Close" on the other devices' header (#142). */
    val onHideOther: () -> Unit = {},
    /** Live result count for an edited filter. */
    val countFor: (DeviceFilter) -> Int = { 0 },
    val onRefreshOrder: () -> Unit = {},
    /** Opens find mode for the device. */
    val onDeviceClick: (BleDevice) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

/**
 * Nearby tab (design home-header-v4): top bar with search, pause/resume and more; one 40 dp control
 * row (status, sort, filter); then the list. Filters live in a bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanContent(
    title: String,
    state: ScanUiState,
    permissionBanner: PermissionBanner?,
    actions: ScanActions,
    modifier: Modifier = Modifier,
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val onSortNow: () -> Unit = {
        actions.onRefreshOrder()
        scope.launch { listState.animateScrollToItem(0) }
    }
    ReorderAutoSort(state.reorderAvailable, listState, actions.onRefreshOrder)

    Scaffold(
        modifier = modifier,
        topBar = { ScanTopBar(title, state, actions, searchOpen, onSearchOpenChange = { searchOpen = it }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ControlRow(state, actions, onSortNow, onOpenFilters = { filtersOpen = true })

            val error = (state.status as? ScanStatus.Error)?.reason
            // Nothing to show and the radio (or Location on Android 11) is off: a full empty state (#30).
            val radioOff = error == ScanFailure.BLUETOOTH_OFF || error == ScanFailure.LOCATION_OFF
            if (error != null && radioOff && state.items.isEmpty()) {
                RadioOffState(error, actions, Modifier.fillMaxWidth().weight(1f))
            } else {
                DeviceList(
                    state = state,
                    permissionBanner = permissionBanner,
                    error = error,
                    actions = actions,
                    listState = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }

    if (filtersOpen) {
        FilterDialog(
            current = state.preferences.filter,
            census = state.census,
            countFor = actions.countFor,
            onApply = {
                actions.onApplyFilter(it)
                filtersOpen = false
            },
            onClose = { filtersOpen = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanTopBar(
    title: String,
    state: ScanUiState,
    actions: ScanActions,
    searchOpen: Boolean,
    onSearchOpenChange: (Boolean) -> Unit,
) {
    TopAppBar(
        title = {
            if (searchOpen) {
                TextField(
                    value = state.query,
                    onValueChange = actions.onQueryChange,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(title)
            }
        },
        actions = {
            IconButton(
                onClick = {
                    if (searchOpen) actions.onQueryChange("")
                    onSearchOpenChange(!searchOpen)
                },
            ) {
                val searchLabel =
                    stringResource(if (searchOpen) R.string.action_close_search else R.string.action_search)
                if (searchOpen) {
                    Icon(Icons.Default.Close, contentDescription = searchLabel)
                } else {
                    Icon(painterResource(UiR.drawable.ic_search), contentDescription = searchLabel)
                }
            }
            if (!searchOpen) {
                val scanning = state.isScanning
                IconButton(onClick = if (scanning) actions.onPause else actions.onResume) {
                    Icon(
                        painter = painterResource(if (scanning) UiR.drawable.ic_pause else UiR.drawable.ic_play),
                        contentDescription = stringResource(
                            if (scanning) R.string.action_pause_scan else R.string.action_resume_scan,
                        ),
                    )
                }
                MoreMenu(actions)
            }
        },
    )
}

@Composable
private fun MoreMenu(actions: ScanActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_clear_list)) },
                onClick = {
                    expanded = false
                    actions.onReset()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(UiR.string.menu_settings)) },
                onClick = {
                    expanded = false
                    actions.onOpenSettings()
                },
            )
        }
    }
}

/** Status dot and text on the left, sort and filter on the right; 40 dp tall. */
@Composable
private fun ControlRow(state: ScanUiState, actions: ScanActions, onSortNow: () -> Unit, onOpenFilters: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A new order is offered in place of the status, so the list never moves for it (#87).
        Crossfade(
            targetState = state.reorderAvailable,
            modifier = Modifier.weight(1f),
            animationSpec = tween(CHIP_FADE_MS),
            label = "statusOrReorder",
        ) { reorder ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (reorder) ReorderChip(onClick = onSortNow) else StatusText(state)
            }
        }
        SortMenu(selected = state.preferences.sort, onSelect = actions.onSortChange)
        FilterButton(count = state.activeFilterCount, onClick = onOpenFilters)
    }
}

@Composable
private fun StatusText(state: ScanUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(scanning = state.isScanning)
        Spacer(Modifier.width(8.dp))
        // "N nearby" counts personal devices, plus other devices once they are listed (#82, #100).
        val withOther = state.preferences.filter.showOther || state.items.any { it.isOther }
        val shown = state.items.size
        val total = state.census.personal + if (withOther) state.census.other else 0
        // "N / M" only when something hides devices; showing other devices only adds rows.
        val filtered = state.activeFilterCount > 0 || state.query.isNotBlank()
        Text(
            text = when {
                filtered && state.isPaused ->
                    stringResource(R.string.scan_status_paused_filtered, shown, total)
                filtered -> stringResource(R.string.scan_status_filtered, shown, total)
                state.isPaused -> stringResource(R.string.scan_status_paused, total)
                else -> stringResource(R.string.scan_status_nearby, total)
            },
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReorderChip(onClick: () -> Unit) {
    val description = stringResource(R.string.home_reorder_chip_description)
    AssistChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.home_reorder_chip), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.height(28.dp).semantics { contentDescription = description },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        border = null,
    )
}

/**
 * Without a tap the new order is applied after [REORDER_AUTO_MS], or as soon as the user scrolls back to
 * the top of the list, where rows moving is no surprise (#87).
 */
@Composable
private fun ReorderAutoSort(available: Boolean, listState: LazyListState, onSort: () -> Unit) {
    val currentOnSort by rememberUpdatedState(onSort)
    LaunchedEffect(available) {
        if (!available) return@LaunchedEffect
        launch {
            delay(REORDER_AUTO_MS)
            currentOnSort()
        }
        snapshotFlow { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
            .distinctUntilChanged()
            .drop(1)
            .filter { it }
            .collect { currentOnSort() }
    }
}

/** Green and pulsing while scanning, grey when paused or stopped. */
@Composable
private fun StatusDot(scanning: Boolean) {
    val color = if (scanning) NearoTheme.colors.signalStrong else MaterialTheme.colorScheme.outline
    val alpha = if (scanning) {
        val pulse = rememberInfiniteTransition(label = "scanPulse")
        pulse.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
            label = "scanPulseAlpha",
        ).value
    } else {
        1f
    }
    Box(
        Modifier
            .size(8.dp)
            .alpha(alpha)
            .background(color, CircleShape),
    )
}

@Composable
private fun FilterButton(count: Int, onClick: () -> Unit) {
    val description = if (count > 0) {
        stringResource(R.string.filter_active_description, count)
    } else {
        stringResource(R.string.filter_title)
    }
    if (count > 0) {
        // The count sits inside the button in primary colours; red would read as an error (#89).
        Box(Modifier.size(FILTER_BUTTON)) {
            FilledTonalIconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
                Icon(painterResource(UiR.drawable.ic_filter), contentDescription = description)
            }
            Badge(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp).size(15.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(count.toString(), style = MaterialTheme.typography.labelSmall)
            }
        }
    } else {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(painterResource(UiR.drawable.ic_filter), contentDescription = description)
        }
    }
}

@Composable
private fun DeviceList(
    state: ScanUiState,
    permissionBanner: PermissionBanner?,
    error: ScanFailure?,
    actions: ScanActions,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    var otherInfoOpen by rememberSaveable { mutableStateOf(false) }
    if (otherInfoOpen) OtherInfoSheet(onDismiss = { otherInfoOpen = false })
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        // Permission and error banners stay the first list item (design home-header-v4 §6).
        permissionBanner?.let { banner ->
            item(key = "permission") {
                StatusBanner(
                    message = stringResource(
                        when (banner) {
                            PermissionBanner.OPEN_SETTINGS -> R.string.permission_denied_settings
                            PermissionBanner.ASK_AGAIN -> R.string.permission_rationale
                        },
                    ),
                    actionLabel = stringResource(
                        when (banner) {
                            PermissionBanner.OPEN_SETTINGS -> R.string.action_open_settings
                            PermissionBanner.ASK_AGAIN -> R.string.action_allow
                        },
                    ),
                    onAction = actions.onPermissionAction,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        error?.let { reason ->
            item(key = "error") { ScanErrorBanner(reason, actions, Modifier.padding(horizontal = 16.dp)) }
        }
        val personal = state.items.filter { !it.isOther }
        val other = state.items.filter { it.isOther }
        if (state.items.isEmpty() && state.isScanning && state.hiddenOtherCount == 0) {
            item(key = "searching") {
                Text(
                    text = stringResource(R.string.scan_searching),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (personal.isEmpty() && (other.isNotEmpty() || state.hiddenOtherCount > 0)) {
            item(key = "empty-personal") {
                Text(
                    text = stringResource(R.string.nearby_empty_personal),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        // Stable keys keep each row attached to its device; rows only move on a full sort.
        items(personal, key = { it.device.address }) { item -> DeviceRow(item, actions, Modifier.animateItem()) }
        if (state.hiddenOtherCount > 0) {
            item(key = "other-hidden") {
                HiddenOtherRow(state.hiddenOtherCount, actions.onShowOther, onInfo = { otherInfoOpen = true })
            }
        }
        if (other.isNotEmpty()) {
            item(key = "other-header") {
                OtherHeader(other.size, onClose = actions.onHideOther, onInfo = { otherInfoOpen = true })
            }
            items(other, key = { it.device.address }) { item ->
                DeviceRow(item, actions, Modifier.animateItem(), compact = true)
            }
        }
    }
}

@Composable
private fun DeviceRow(item: DeviceListItem, actions: ScanActions, modifier: Modifier, compact: Boolean = false) {
    DeviceCard(
        device = item.device,
        label = item.label,
        showSuffix = item.showSuffix,
        isPaired = item.isPaired,
        isNew = item.isNew,
        isGone = item.isGone,
        distanceMeters = item.distanceMeters,
        onClick = { actions.onDeviceClick(item.device) },
        modifier = modifier,
        compact = compact,
    )
}

/** "23 other devices hidden · Show": one centred line, the whole row is the 48 dp touch target (#82). */
@Composable
private fun HiddenOtherRow(count: Int, onShow: () -> Unit, onInfo: () -> Unit) {
    val show = stringResource(R.string.nearby_other_show)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onShow)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(R.plurals.nearby_other_hidden, count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = show,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        OtherInfoButton(onInfo)
    }
}

/** ⓘ beside "other devices": opens the short explanation of what they are (#142). */
@Composable
private fun OtherInfoButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = stringResource(R.string.nearby_other_info),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What "other devices" are, why they start hidden, and what to do if one of them is yours (#142). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OtherInfoSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.other_info_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.other_info_what), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.other_info_why), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.other_info_mine), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.other_info_ok))
            }
        }
    }
}

/** "Other devices · 23", ⓘ, and "Close" at the end, which hides the section again (#142). */
@Composable
private fun OtherHeader(count: Int, onClose: () -> Unit, onInfo: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(top = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.nearby_other_section, count),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OtherInfoButton(onInfo)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text(stringResource(R.string.nearby_other_close)) }
        }
    }
}

@Composable
private fun SortMenu(selected: DeviceSort, onSelect: (DeviceSort) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        ) {
            Icon(
                painterResource(UiR.drawable.ic_sort),
                contentDescription = stringResource(R.string.sort_menu),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(stringResource(selected.labelRes()), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DeviceSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(stringResource(sort.labelRes())) },
                    onClick = {
                        expanded = false
                        onSelect(sort)
                    },
                )
            }
        }
    }
}

private fun DeviceSort.labelRes(): Int = when (this) {
    DeviceSort.NEAREST -> R.string.sort_nearest
    DeviceSort.NAME -> R.string.sort_name
    DeviceSort.RECENT -> R.string.sort_recent
}

private const val PULSE_MS = 1_000
private const val CHIP_FADE_MS = 150
private val FILTER_BUTTON = 34.dp
private const val REORDER_AUTO_MS = 8_000L

@Composable
private fun RadioOffState(reason: ScanFailure, actions: ScanActions, modifier: Modifier = Modifier) {
    val bluetooth = reason == ScanFailure.BLUETOOTH_OFF
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        IllustratedMessage(
            icon = if (bluetooth) NearoIcons.BluetoothOff else NearoIcons.PinSignal,
            title = stringResource(
                if (bluetooth) R.string.empty_bluetooth_off_title else R.string.empty_location_off_title,
            ),
            body = stringResource(if (bluetooth) R.string.empty_bluetooth_off_body else R.string.error_location_off),
        ) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = if (bluetooth) actions.onEnableBluetooth else actions.onEnableLocation) {
                Text(stringResource(R.string.action_turn_on))
            }
        }
    }
}

@Composable
private fun ScanErrorBanner(reason: ScanFailure, actions: ScanActions, modifier: Modifier = Modifier) {
    val (message, actionLabel, onAction) = when (reason) {
        ScanFailure.BLUETOOTH_OFF ->
            Triple(R.string.error_bluetooth_off, R.string.action_turn_on, actions.onEnableBluetooth)
        ScanFailure.LOCATION_OFF ->
            Triple(R.string.error_location_off, R.string.action_turn_on, actions.onEnableLocation)
        ScanFailure.NOT_SUPPORTED ->
            Triple(R.string.error_not_supported, R.string.action_ok, actions.onDismissError)
        ScanFailure.PERMISSION_MISSING ->
            Triple(R.string.permission_rationale, R.string.action_ok, actions.onDismissError)
        ScanFailure.TOO_FREQUENT ->
            Triple(R.string.error_too_frequent, R.string.action_retry, actions.onRetry)
        ScanFailure.INTERNAL ->
            Triple(R.string.error_scan_failed, R.string.action_retry, actions.onRetry)
    }
    StatusBanner(
        message = stringResource(message),
        actionLabel = stringResource(actionLabel),
        onAction = onAction,
        modifier = modifier,
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScanContentPreview() {
    val now = 1_000_000L
    fun device(address: String, name: String, rssi: Int, lastSeen: Long = now) = BleDevice(
        address = address,
        name = name,
        rssi = rssi,
        firstSeen = 0,
        lastSeen = lastSeen,
        manufacturer = "Samsung",
    )
    NearoTheme {
        ScanContent(
            title = "Nearo",
            state = ScanUiState(
                status = ScanStatus.Scanning,
                items = listOf(
                    DeviceListItem(device("A", "Galaxy Buds2 Pro", -52), isNew = false, isGone = false),
                    DeviceListItem(device("B", "Galaxy Watch6", -70, lastSeen = 0), isNew = false, isGone = true),
                    DeviceListItem(device("C", "Tile Mate", -80), isNew = true, isGone = false),
                ),
                reorderAvailable = true,
                totalCount = 3,
                census = ScanCensus(personal = 3, other = 23),
                hiddenOtherCount = 23,
            ),
            permissionBanner = null,
            actions = ScanActions(),
        )
    }
}
