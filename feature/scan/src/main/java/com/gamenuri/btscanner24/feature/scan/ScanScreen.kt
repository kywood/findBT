package com.gamenuri.btscanner24.feature.scan

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamenuri.btscanner24.core.ble.BleScanner
import com.gamenuri.btscanner24.core.ble.ScanFailure
import com.gamenuri.btscanner24.core.designsystem.component.StatusBanner
import com.gamenuri.btscanner24.core.ui.DeviceCard
import com.gamenuri.btscanner24.core.ui.permission.rememberBlePermissionState
import com.gamenuri.btscanner24.feature.scan.R

@OptIn(ExperimentalMaterial3Api::class)
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

    fun onScanClick() {
        when {
            state.isScanning -> viewModel.stopScan()
            !permission.isGranted -> permission.request()
            else -> viewModel.startScan()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = {
                    IconButton(onClick = viewModel::reset) {
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
            Button(onClick = ::onScanClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.isScanning) R.string.action_stop_scan else R.string.action_start_scan))
            }

            if (permission.showRationale) {
                StatusBanner(
                    message = stringResource(
                        if (permission.isPermanentlyDenied) {
                            R.string.permission_denied_settings
                        } else {
                            R.string.permission_rationale
                        },
                    ),
                    actionLabel = stringResource(
                        if (permission.isPermanentlyDenied) R.string.action_open_settings else R.string.action_allow,
                    ),
                    onAction = permission::request,
                )
            }

            (state.status as? ScanStatus.Error)?.let { error ->
                ScanErrorBanner(
                    reason = error.reason,
                    onEnableBluetooth = {
                        viewModel.dismissError()
                        enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    },
                    onRetry = viewModel::startScan,
                    onDismiss = viewModel::dismissError,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = state.filter.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = state.filter.sortType == SortType.FIRST_SEEN,
                    onClick = { viewModel.setSortType(SortType.FIRST_SEEN) },
                    label = { ChipLabel(R.string.sort_first_seen) },
                    modifier = Modifier.padding(end = 6.dp),
                )
                FilterChip(
                    selected = state.filter.sortType == SortType.RSSI,
                    onClick = { viewModel.setSortType(SortType.RSSI) },
                    label = { ChipLabel(R.string.sort_signal) },
                    modifier = Modifier.padding(end = 6.dp),
                )
                FilterChip(
                    selected = state.filter.namedOnly,
                    onClick = viewModel::toggleNamedOnly,
                    label = { ChipLabel(R.string.filter_named_only) },
                )
                IconButton(onClick = viewModel::toggleSortLock) {
                    Icon(
                        imageVector = if (state.filter.sortLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = stringResource(
                            if (state.filter.sortLocked) R.string.sort_locked else R.string.sort_unlocked,
                        ),
                        tint = if (state.filter.sortLocked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }

            Text(
                text = pluralStringResource(R.plurals.devices_found, state.devices.size, state.devices.size),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Stable keys keep each card (and its scroll position) attached to its device when the order changes.
                items(state.devices, key = { it.address }) { device ->
                    DeviceCard(device = device, modifier = Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
private fun ScanErrorBanner(
    reason: ScanFailure,
    onEnableBluetooth: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (reason) {
        ScanFailure.BLUETOOTH_OFF -> StatusBanner(
            message = stringResource(R.string.error_bluetooth_off),
            actionLabel = stringResource(R.string.action_turn_on),
            onAction = onEnableBluetooth,
        )
        ScanFailure.NOT_SUPPORTED -> StatusBanner(
            message = stringResource(R.string.error_not_supported),
            actionLabel = stringResource(R.string.action_ok),
            onAction = onDismiss,
        )
        ScanFailure.PERMISSION_MISSING -> StatusBanner(
            message = stringResource(R.string.permission_rationale),
            actionLabel = stringResource(R.string.action_ok),
            onAction = onDismiss,
        )
        ScanFailure.TOO_FREQUENT -> StatusBanner(
            message = stringResource(R.string.error_too_frequent),
            actionLabel = stringResource(R.string.action_retry),
            onAction = onRetry,
        )
        ScanFailure.INTERNAL -> StatusBanner(
            message = stringResource(R.string.error_scan_failed),
            actionLabel = stringResource(R.string.action_retry),
            onAction = onRetry,
        )
    }
}

@Composable
private fun ChipLabel(textRes: Int) {
    Text(stringResource(textRes), style = MaterialTheme.typography.labelSmall)
}
