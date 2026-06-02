package com.gamenuri.findbt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BleViewModel : ViewModel() {

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices

    private val deviceMap = mutableMapOf<String, BleDevice>()

    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: "이름 없음"
            val address = result.device.address
            val rssi = result.rssi

            deviceMap[address] = BleDevice(name, address, rssi)
            _devices.value = deviceMap.values.sortedByDescending { it.rssi }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(context: Context) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        scanner = manager.adapter.bluetoothLeScanner

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(null, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanner?.stopScan(scanCallback)
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
    }
}