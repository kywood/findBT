package com.gamenuri.btscanner24.feature.scan

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import com.gamenuri.btscanner24.core.model.CategoryGroup
import com.gamenuri.btscanner24.core.model.DeviceSort
import com.gamenuri.btscanner24.core.model.ListPreferences
import com.gamenuri.btscanner24.core.ui.DeviceCard
import com.gamenuri.btscanner24.core.ui.R as UiR
import com.gamenuri.btscanner24.core.ui.label
import com.gamenuri.btscanner24.core.ui.permission.rememberBlePermissionState

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
            onToggleNamedOnly = viewModel::toggleNamedOnly,
            onToggleHideGone = viewModel::toggleHideGone,
            onToggleGroup = viewModel::toggleGroup,
            onClearGroups = viewModel::clearGroups,
            onResetFilters = viewModel::resetFilters,
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
    val onToggleNamedOnly: () -> Unit = {},
    val onToggleHideGone: () -> Unit = {},
    val onToggleGroup: (CategoryGroup) -> Unit = {},
    val onClearGroups: () -> Unit = {},
    val onResetFilters: () -> Unit = {},
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

    Scaffold(
        modifier = modifier,
        topBar = { ScanTopBar(title, state, actions, searchOpen, onSearchOpenChange = { searchOpen = it }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ControlRow(state, actions, onOpenFilters = { filtersOpen = true })

            val error = (state.status as? ScanStatus.Error)?.reason
            // Nothing to show and the radio (or Location on Android 11) is off: a full empty state (#30).
            val radioOff = error == ScanFailure.BLUETOOTH_OFF || error == ScanFailure.LOCATION_OFF
            if (error != null && radioOff && state.items.isEmpty()) {
                RadioOffState(error, actions, Modifier.fillMaxWidth().weight(1f))
            } else {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    DeviceList(state, permissionBanner, error, actions)
                    // Floats over the list so it never pushes rows down (#62).
                    if (state.reorderAvailable) {
                        ReorderPill(
                            onClick = actions.onRefreshOrder,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (filtersOpen) {
        FilterSheet(state.preferences, actions, onDismiss = { filtersOpen = false })
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
                Icon(
                    imageVector = if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = stringResource(
                        if (searchOpen) R.string.action_close_search else R.string.action_search,
                    ),
                )
            }
            if (!searchOpen) {
                val scanning = state.isScanning
                IconButton(onClick = if (scanning) actions.onPause else actions.onResume) {
                    Icon(
                        imageVector = if (scanning) Icons.Default.Pause else Icons.Default.PlayArrow,
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
private fun ControlRow(state: ScanUiState, actions: ScanActions, onOpenFilters: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(scanning = state.isScanning)
        Spacer(Modifier.width(8.dp))
        val shown = state.items.size
        val filtered = state.activeFilterCount > 0 || state.query.isNotBlank()
        Text(
            text = when {
                filtered -> stringResource(R.string.scan_status_filtered, shown, state.totalCount)
                state.isPaused -> stringResource(R.string.scan_status_paused, state.totalCount)
                else -> stringResource(R.string.scan_status_nearby, state.totalCount)
            },
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        SortMenu(selected = state.preferences.sort, onSelect = actions.onSortChange)
        FilterButton(count = state.activeFilterCount, onClick = onOpenFilters)
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
        BadgedBox(badge = { Badge { Text(count.toString()) } }) {
            FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Tune, contentDescription = description)
            }
        }
    } else {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Tune, contentDescription = description)
        }
    }
}

@Composable
private fun DeviceList(
    state: ScanUiState,
    permissionBanner: PermissionBanner?,
    error: ScanFailure?,
    actions: ScanActions,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
        if (state.items.isEmpty() && state.isScanning) {
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
        // Stable keys keep each row attached to its device; rows only move on a full sort.
        items(state.items, key = { it.device.address }) { item ->
            DeviceCard(
                device = item.device,
                label = item.label,
                showSuffix = item.showSuffix,
                isPaired = item.isPaired,
                isNew = item.isNew,
                isGone = item.isGone,
                distanceMeters = item.distanceMeters,
                onClick = { actions.onDeviceClick(item.device) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun ReorderPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 3.dp,
    ) {
        Text(
            text = stringResource(R.string.reorder_pill),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** Type chips (multi-select, "All" clears them) and two switches; changes apply at once. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(prefs: ListPreferences, actions: ScanActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.filter_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.filter_types),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = prefs.groups.isEmpty(),
                    onClick = actions.onClearGroups,
                    label = { Text(stringResource(R.string.filter_all)) },
                )
                CategoryGroup.entries.forEach { group ->
                    FilterChip(
                        selected = group in prefs.groups,
                        onClick = { actions.onToggleGroup(group) },
                        label = { Text(group.label()) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SwitchRow(R.string.filter_named_only, prefs.namedOnly, actions.onToggleNamedOnly)
            SwitchRow(R.string.filter_hide_gone, prefs.hideGone, actions.onToggleHideGone)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = actions.onResetFilters) { Text(stringResource(R.string.filter_reset)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onDismiss) { Text(stringResource(R.string.filter_done)) }
            }
        }
    }
}

@Composable
private fun SwitchRow(text: Int, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(text), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
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
                Icons.AutoMirrored.Filled.Sort,
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
            ),
            permissionBanner = null,
            actions = ScanActions(),
        )
    }
}
