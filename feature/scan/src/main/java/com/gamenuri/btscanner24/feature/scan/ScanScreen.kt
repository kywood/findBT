package com.gamenuri.btscanner24.feature.scan

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamenuri.btscanner24.core.ble.BleScanner
import com.gamenuri.btscanner24.core.ble.ScanFailure
import com.gamenuri.btscanner24.core.designsystem.component.StatusBanner
import com.gamenuri.btscanner24.core.designsystem.theme.FindBTTheme
import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.CategoryGroup
import com.gamenuri.btscanner24.core.model.DeviceSort
import com.gamenuri.btscanner24.core.ui.DeviceCard
import com.gamenuri.btscanner24.core.ui.label
import com.gamenuri.btscanner24.core.ui.permission.rememberBlePermissionState

/** Entry point: owns the ViewModel, permissions and system intents. Everything visual is in [ScanContent]. */
@Composable
fun ScanScreen(title: String, viewModel: ScanViewModel = viewModel(factory = ScanViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Stop scanning whenever the screen is not visible; resume when it comes back.
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
        title = title,
        state = state,
        permissionBanner = when {
            !permission.showRationale -> null
            permission.isPermanentlyDenied -> PermissionBanner.OPEN_SETTINGS
            else -> PermissionBanner.ASK_AGAIN
        },
        actions = ScanActions(
            onScanClick = {
                when {
                    state.isScanning -> viewModel.stopScan()
                    !permission.isGranted -> permission.request()
                    else -> viewModel.startScan()
                }
            },
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
            onRefreshOrder = viewModel::refreshOrder,
        ),
    )
}

enum class PermissionBanner { ASK_AGAIN, OPEN_SETTINGS }

/** All user events of the scan screen, so [ScanContent] stays stateless and previewable. */
data class ScanActions(
    val onScanClick: () -> Unit = {},
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
    val onRefreshOrder: () -> Unit = {},
)

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

    Scaffold(
        modifier = modifier,
        topBar = {
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
                            searchOpen = !searchOpen
                        },
                    ) {
                        Icon(
                            imageVector = if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = stringResource(
                                if (searchOpen) R.string.action_close_search else R.string.action_search,
                            ),
                        )
                    }
                    IconButton(onClick = actions.onReset) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_reset))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Button(onClick = actions.onScanClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.isScanning) R.string.action_stop_scan else R.string.action_start_scan))
            }

            permissionBanner?.let { banner ->
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
                )
            }

            (state.status as? ScanStatus.Error)?.let { error ->
                ScanErrorBanner(reason = error.reason, actions = actions)
            }

            Spacer(modifier = Modifier.height(6.dp))
            ListControls(state = state, actions = actions)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = pluralStringResource(R.plurals.devices_found, state.items.size, state.items.size),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                )
                if (state.reorderAvailable) {
                    FilledTonalButton(onClick = actions.onRefreshOrder) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null)
                        Text(stringResource(R.string.action_refresh_order), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Stable keys keep each card attached to its device; rows only move on a full sort.
                items(state.items, key = { it.device.address }) { item ->
                    DeviceCard(
                        device = item.device,
                        isNew = item.isNew,
                        isGone = item.isGone,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ListControls(state: ScanUiState, actions: ScanActions) {
    val prefs = state.preferences
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        SortMenu(selected = prefs.sort, onSelect = actions.onSortChange)
        FilterChip(
            selected = prefs.namedOnly,
            onClick = actions.onToggleNamedOnly,
            label = { ChipLabel(stringResource(R.string.filter_named_only)) },
        )
        FilterChip(
            selected = prefs.hideGone,
            onClick = actions.onToggleHideGone,
            label = { ChipLabel(stringResource(R.string.filter_hide_gone)) },
        )
        CategoryGroup.entries.forEach { group ->
            FilterChip(
                selected = group in prefs.groups,
                onClick = { actions.onToggleGroup(group) },
                label = { ChipLabel(group.label()) },
            )
        }
    }
}

@Composable
private fun SortMenu(selected: DeviceSort, onSelect: (DeviceSort) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(selected.labelRes()), style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.sort_menu))
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

@Composable
private fun ScanErrorBanner(reason: ScanFailure, actions: ScanActions) {
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
    StatusBanner(message = stringResource(message), actionLabel = stringResource(actionLabel), onAction = onAction)
}

@Composable
private fun ChipLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall)
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
    FindBTTheme(dynamicColor = false) {
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
            ),
            permissionBanner = null,
            actions = ScanActions(),
        )
    }
}
