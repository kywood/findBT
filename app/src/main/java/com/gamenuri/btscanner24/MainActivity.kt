package com.gamenuri.btscanner24


import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gamenuri.btscanner24.ui.theme.FindBTTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen

class MainActivity : ComponentActivity() {

    private val viewModel: BleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FindBTTheme {
                ScanScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(viewModel: BleViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val devices by viewModel.devices.collectAsState()
    val sortType by viewModel.sortType.collectAsState()
    val showNamedOnly by viewModel.showNamedOnly.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val permissionState = rememberMultiplePermissionsState(permissions)
    var isScanning by remember { mutableStateOf(false) }
    val isSortLocked by viewModel.isSortLocked.collectAsState()


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BT Scanner 24") },
                actions = {
                    // 초기화 버튼 (상단 우측)
                    IconButton(onClick = {
                        viewModel.stopScan()
                        viewModel.reset()
                        viewModel.setSearchQuery("")
                        isScanning = false
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "초기화")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding() - 8.dp)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            // 스캔 버튼
            Button(
                onClick = {
                    if (!permissionState.allPermissionsGranted) {
                        permissionState.launchMultiplePermissionRequest()
                    } else {
                        if (isScanning) viewModel.stopScan() else viewModel.startScan(context)
                        isScanning = !isScanning
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isScanning) "⏹ Stop Scan" else "🔍 Start Scan")
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 검색창
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search device name...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 정렬 + 필터 버튼
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = sortType == SortType.FIRST_SEEN,
                    onClick = { viewModel.setSortType(SortType.FIRST_SEEN) },
                    label = { Text("First Seen", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.padding(end = 6.dp)
                )
                FilterChip(
                    selected = sortType == SortType.RSSI,
                    onClick = { viewModel.setSortType(SortType.RSSI) },
                    label = { Text("Signal", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.padding(end = 6.dp)
                )
                FilterChip(
                    selected = showNamedOnly,
                    onClick = { viewModel.toggleShowNamedOnly() },
                    label = { Text("Named Only", style = MaterialTheme.typography.labelSmall) }
                )
                IconButton(onClick = { viewModel.toggleSortLock() }) {
                    Icon(
                        imageVector = if (isSortLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (isSortLocked) "Sort Locked" else "Sort Unlocked",
                        tint = if (isSortLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                "${devices.size} devices found",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // 기기 목록
            LazyColumn {
                items(devices) { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(device.name, style = MaterialTheme.typography.bodyMedium)
                            Text(device.address, style = MaterialTheme.typography.labelSmall)
                            Text("${device.deviceType}  |  ${device.manufacturer}", style = MaterialTheme.typography.labelSmall)
                            Text("RSSI: ${device.rssi} dBm (${device.rssiLevel}) | Found: ${device.firstSeenFormatted}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}