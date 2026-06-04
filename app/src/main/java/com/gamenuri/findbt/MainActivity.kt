package com.gamenuri.findbt


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
import com.gamenuri.findbt.ui.theme.FindBTTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

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

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val permissionState = rememberMultiplePermissionsState(permissions)
    var isScanning by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("findBT") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
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
                Text(if (isScanning) "⏹ 스캔 중지" else "🔍 스캔 시작")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 정렬 버튼
            Row {
                FilterChip(
                    selected = sortType == SortType.FIRST_SEEN,
                    onClick = { viewModel.setSortType(SortType.FIRST_SEEN) },
                    label = { Text("발견 순") },
                    modifier = Modifier.padding(end = 8.dp)
                )
                FilterChip(
                    selected = sortType == SortType.RSSI,
                    onClick = { viewModel.setSortType(SortType.RSSI) },
                    label = { Text("신호 세기 순") },
                    modifier = Modifier.padding(end = 8.dp)
                )
                FilterChip(
                    selected = showNamedOnly,
                    onClick = { viewModel.toggleShowNamedOnly() },
                    label = { Text("이름 있는 기기만") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("${devices.size}개 기기 발견")
            Spacer(modifier = Modifier.height(8.dp))

            // 기기 목록
            LazyColumn {
                items(devices) { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(device.name, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(device.address, style = MaterialTheme.typography.bodySmall)
                            Text("${device.deviceType}")
                            Text("제조사: ${device.manufacturer}")
                            Text("신호: ${device.rssi} dBm")
                            Text("발견: ${device.firstSeenFormatted}")
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "RAW: ${device.rawBytes}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}