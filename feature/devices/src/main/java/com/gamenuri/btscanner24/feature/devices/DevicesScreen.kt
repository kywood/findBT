package com.gamenuri.btscanner24.feature.devices

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamenuri.btscanner24.core.ble.BleScanner
import com.gamenuri.btscanner24.core.ble.ScanFailure
import com.gamenuri.btscanner24.core.designsystem.component.StatusBanner
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.DeviceCategory
import com.gamenuri.btscanner24.core.model.PairedDevice
import com.gamenuri.btscanner24.core.model.SignalLevel
import com.gamenuri.btscanner24.core.ui.DeviceIcon
import com.gamenuri.btscanner24.core.ui.R as UiR
import com.gamenuri.btscanner24.core.ui.SignalBars
import com.gamenuri.btscanner24.core.ui.distanceText
import com.gamenuri.btscanner24.core.ui.help.NotDetectedHelpSheet
import com.gamenuri.btscanner24.core.ui.label
import com.gamenuri.btscanner24.core.ui.permission.rememberBlePermissionState

/** Entry point of the My Devices tab: ViewModel, lifecycle and permission. Visuals in [DevicesContent]. */
@Composable
fun DevicesScreen(
    onDeviceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAddTracker: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    /** "Rename or change type" on a saved device (#108); the app shows the sheet. */
    onEditSaved: (MyDeviceItem) -> Unit = {},
    /** How many saves are free, for the count pill; null with Premium or a day pass. */
    freeSaveLimit: Int? = null,
    viewModel: DevicesViewModel = viewModel(factory = DevicesViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.onScreenStarted()
        onStopOrDispose { viewModel.onScreenStopped() }
    }
    val permission = rememberBlePermissionState(
        permissions = BleScanner.requiredPermissions(),
        onGranted = viewModel::onScreenStarted,
    )
    val context = LocalContext.current
    DevicesContent(
        modifier = modifier,
        state = state.copy(hasPermission = state.hasPermission && permission.isGranted),
        onDeviceClick = { onDeviceClick(it.findAddress) },
        onHide = viewModel::hide,
        onRequestPermission = permission::request,
        onOpenBluetoothSettings = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
        onAddTracker = onAddTracker,
        onOpenSettings = onOpenSettings,
        onRetryScan = viewModel::onScreenStarted,
        saved = SavedActions(onRemove = viewModel::remove, onEdit = onEditSaved, freeLimit = freeSaveLimit),
    )
}

/** What the saved devices section needs (#108). */
data class SavedActions(
    val onRemove: (MyDeviceItem) -> Unit = {},
    val onEdit: (MyDeviceItem) -> Unit = {},
    val freeLimit: Int? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesContent(
    state: MyDevicesUiState,
    onDeviceClick: (MyDeviceItem) -> Unit,
    onHide: (MyDeviceItem) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onAddTracker: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onRetryScan: () -> Unit = {},
    saved: SavedActions = SavedActions(),
    now: Long = System.currentTimeMillis(),
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.devices_title)) },
                actions = { SettingsMenu(onOpenSettings) },
            )
        },
    ) { padding ->
        when {
            !state.hasPermission -> Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
                StatusBanner(
                    message = stringResource(R.string.devices_permission_body),
                    actionLabel = stringResource(R.string.devices_permission_action),
                    onAction = onRequestPermission,
                )
            }
            state.isEmpty -> EmptyState(onOpenBluetoothSettings, Modifier.padding(padding))
            else -> Column(Modifier.padding(padding)) {
                // Without a scan nothing can be marked nearby: say why instead of showing everything as away.
                state.scanFailure?.let { reason ->
                    StatusBanner(
                        message = stringResource(
                            when (reason) {
                                ScanFailure.BLUETOOTH_OFF -> R.string.devices_scan_bluetooth_off
                                ScanFailure.LOCATION_OFF -> R.string.devices_scan_location_off
                                else -> R.string.devices_scan_failed
                            },
                        ),
                        actionLabel = stringResource(R.string.devices_scan_retry),
                        onAction = onRetryScan,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                DeviceSections(state, now, onDeviceClick, onHide, onAddTracker, saved)
            }
        }
    }
}

@Composable
private fun DeviceSections(
    state: MyDevicesUiState,
    now: Long,
    onDeviceClick: (MyDeviceItem) -> Unit,
    onHide: (MyDeviceItem) -> Unit,
    onAddTracker: () -> Unit,
    saved: SavedActions,
    modifier: Modifier = Modifier,
) {
    var othersOpen by rememberSaveable { mutableStateOf(false) }
    var helpOpen by rememberSaveable { mutableStateOf(false) }
    if (helpOpen) NotDetectedHelpSheet(onDismiss = { helpOpen = false })
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // Plain full-width rows like the Nearby tab (design home-header-v4); headers keep the 16 dp inset.
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        // Saved by hand (#108): always first, with the count and, for free users, the limit.
        item(key = "h-saved") { SavedHeader(state.savedCount, saved.freeLimit) }
        if (state.savedCount == 0) {
            item(key = "saved-hint") {
                Text(
                    text = stringResource(R.string.devices_saved_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        items(state.saved, key = { "s-" + it.paired.address }) { PairedRow(it, now, onDeviceClick, onHide, saved) }
        if (state.nearby.isNotEmpty()) {
            item(key = "h-nearby") { SectionHeader(stringResource(R.string.devices_section_nearby)) }
            items(state.nearby, key = { "n-" + it.paired.address }) { PairedRow(it, now, onDeviceClick, onHide, saved) }
        }
        if (state.notNearby.isNotEmpty()) {
            item(key = "h-away") { SectionHeader(stringResource(R.string.devices_section_not_nearby)) }
            items(state.notNearby, key = { "a-" + it.paired.address }) {
                PairedRow(it, now, onDeviceClick, onHide, saved)
            }
        }
        if (state.others.isNotEmpty()) {
            item(key = "h-others") {
                TextButton(onClick = { othersOpen = !othersOpen }, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(stringResource(R.string.devices_others, state.others.size))
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.rotate(if (othersOpen) 0f else -90f),
                    )
                }
            }
            if (othersOpen) {
                items(state.others, key = { "o-" + it.paired.address }) {
                    PairedRow(it, now, onDeviceClick, onHide, saved)
                }
            }
        }
        // Why a device may not be picked up and what to try (#154), while one of them is not seen.
        if (state.notNearby.isNotEmpty() || state.saved.any { !it.isNearby }) {
            item(key = "not-detected-help") {
                TextButton(onClick = { helpOpen = true }, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null)
                    Text(
                        stringResource(UiR.string.help_not_detected_link),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        // Trackers are not paired, so they never show up above on their own (design home-v3).
        item(key = "add-tracker") {
            TextButton(onClick = onAddTracker, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.devices_add_tracker), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** "Saved devices" with the count on the right: "1 · 1 free" for free users, just the number otherwise. */
@Composable
private fun SavedHeader(count: Int, freeLimit: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { SectionHeader(stringResource(R.string.devices_section_saved)) }
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp, top = 12.dp),
        ) {
            Text(
                text = if (freeLimit != null) {
                    stringResource(R.string.devices_saved_count_free, count, freeLimit)
                } else {
                    count.toString()
                },
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun PairedRow(
    item: MyDeviceItem,
    now: Long,
    onClick: (MyDeviceItem) -> Unit,
    onHide: (MyDeviceItem) -> Unit,
    saved: SavedActions,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(item) }
            .alpha(if (item.isNearby) 1f else 0.7f)
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeviceIcon(item.paired.category)
        Spacer(Modifier.width(12.dp))
        // A paired device with no name is rare; its type is enough here (no address suffix in My devices).
        val title = item.paired.name?.takeIf { it.isNotBlank() } ?: item.paired.category.label()
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.isSaved) {
                    Icon(
                        Icons.Outlined.BookmarkBorder,
                        contentDescription = stringResource(R.string.devices_saved_mark),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp).size(14.dp),
                    )
                }
            }
            Text(
                text = subtitle(item, now),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val sighting = item.sighting
        val meters = item.distanceMeters
        if (item.isNearby && sighting != null) {
            Column(horizontalAlignment = Alignment.End) {
                SignalBars(level = sighting.signalLevel, label = signalLabel(sighting.signalLevel))
                if (meters != null) {
                    Text(
                        text = distanceText(meters),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.devices_more_for, title))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (item.isSaved) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.devices_saved_edit)) },
                        onClick = {
                            menuOpen = false
                            saved.onEdit(item)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.devices_saved_remove)) },
                        onClick = {
                            menuOpen = false
                            saved.onRemove(item)
                        },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.devices_hide)) },
                        onClick = {
                            menuOpen = false
                            onHide(item)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun subtitle(item: MyDeviceItem, now: Long): String {
    val sighting = item.sighting
    return when {
        item.isNearby -> {
            sighting?.manufacturer?.let {
                stringResource(UiR.string.device_maker_type, it, item.paired.category.label())
            }
                ?: item.paired.category.label()
        }
        sighting == null ->
            stringResource(if (item.isSaved) R.string.devices_saved_not_seen else R.string.devices_not_seen)
        else -> {
            val minutes = ((now - sighting.lastSeen) / 60_000).toInt()
            if (minutes < 1) {
                stringResource(R.string.devices_last_seen_now)
            } else {
                stringResource(R.string.devices_last_seen_minutes, minutes)
            }
        }
    }
}

@Composable
private fun signalLabel(level: SignalLevel): String = stringResource(
    when (level) {
        SignalLevel.STRONG -> UiR.string.signal_strong
        SignalLevel.MEDIUM -> UiR.string.signal_medium
        SignalLevel.WEAK -> UiR.string.signal_weak
    },
)

@Composable
private fun EmptyState(onOpenBluetoothSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DeviceIcon(DeviceCategory.EARBUDS, size = 64.dp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.devices_empty_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.devices_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenBluetoothSettings) { Text(stringResource(R.string.devices_open_bluetooth_settings)) }
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DevicesPreview() {
    val now = 10_000_000L
    val buds = PairedDevice("A", "Galaxy Buds2 Pro", DeviceCategory.EARBUDS)
    val seen = BleDevice(
        address = "X",
        name = "Galaxy Buds2 Pro",
        rssi = -55,
        firstSeen = 0,
        lastSeen = now,
        manufacturer = "Samsung",
    )
    NearoTheme {
        DevicesContent(
            state = MyDevicesUiState(
                nearby = listOf(MyDeviceItem(buds, seen, isNearby = true, distanceMeters = 1.2)),
                notNearby = listOf(
                    MyDeviceItem(PairedDevice("B", "WH-1000XM5", DeviceCategory.HEADPHONES), null, false, null),
                ),
                others = listOf(MyDeviceItem(PairedDevice("C", "My Car", DeviceCategory.UNKNOWN), null, false, null)),
            ),
            onDeviceClick = {},
            onHide = {},
            onRequestPermission = {},
            onOpenBluetoothSettings = {},
            now = now,
        )
    }
}

/** ⋮ on the My devices tab: Settings, the same as on Nearby (#56). */
@Composable
private fun SettingsMenu(onOpenSettings: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(UiR.string.menu_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(UiR.string.menu_settings)) },
                onClick = {
                    expanded = false
                    onOpenSettings()
                },
            )
        }
    }
}
