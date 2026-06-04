package com.gamenuri.findbt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class SortType {
    FIRST_SEEN,
    RSSI
}

class BleViewModel : ViewModel() {

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices

    private val _sortType = MutableStateFlow(SortType.FIRST_SEEN)
    val sortType: StateFlow<SortType> = _sortType

    private val _showNamedOnly = MutableStateFlow(false)
    val showNamedOnly: StateFlow<Boolean> = _showNamedOnly

    private val deviceMap = mutableMapOf<String, BleDevice>()
    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val address = result.device.address
            val rssi = result.rssi

            val rawBytes = result.scanRecord?.bytes
            val parsed = if (rawBytes != null) BleParser.parse(rawBytes) else ParsedAdvertiseData()

            val name = result.device.name ?: result.scanRecord?.deviceName ?: parsed.name
//            val manufacturer = if (parsed.manufacturerId != Constants.UNKNOWN) parsed.manufacturerId else getManufacturer(address)

            val manufacturer = if (parsed.manufacturerId != Constants.UNKNOWN) parsed.manufacturerId else Constants.UNKNOWN

            val deviceType = if (parsed.deviceType != Constants.UNKNOWN) parsed.deviceType else getDeviceType(result)

            val existing = deviceMap[address]
            deviceMap[address] = BleDevice(
                name = name,
                address = address,
                rssi = rssi,
                firstSeen = existing?.firstSeen ?: System.currentTimeMillis(),
                manufacturer = manufacturer,
                deviceType = deviceType,
                uuids = parsed.serviceUuids,
                txPower = parsed.txPower,
                flags = parsed.flags,
                rawBytes = parsed.rawBytes
            )
            updateList()
        }
    }


//    fun ByteArray.toHex(): String = joinToString(":") { "%02X".format(it) }

    private fun updateList() {
        var list = deviceMap.values.toList()

        // 이름 있는 기기만 필터
        if (_showNamedOnly.value) {
            list = list.filter { it.name != Constants.UNKNOWN }
        }

        // 정렬
        _devices.value = when (_sortType.value) {
            SortType.FIRST_SEEN -> list.sortedBy { it.firstSeen }
            SortType.RSSI -> list.sortedByDescending { it.rssi }
        }
    }

    fun toggleShowNamedOnly() {
        _showNamedOnly.value = !_showNamedOnly.value
        updateList()
    }

    fun setSortType(sortType: SortType) {
        _sortType.value = sortType
        updateList()
    }

//    private fun getManufacturer(mac: String): String {
//        val oui = mac.uppercase().replace(":", "").take(6)
//        return when (oui) {
//            "ACDE48", "000000" -> "Apple"
//            "3C2EFF", "F0B429" -> "Samsung"
//            "001A7D", "00E04C" -> "Realtek"
//            "000272" -> "Sony"
//            "00259C" -> "LG"
//            "000F86" -> "Xiaomi"
//            "CC4B73" -> "Huawei"
//            "B8763F" -> "Qualcomm"
//            else -> "${Constants.UNKNOWN} ($oui)"
//        }
//    }

    private fun getDeviceType(result: ScanResult): String {
        val uuids = result.scanRecord?.serviceUuids?.map { it.toString().uppercase() } ?: emptyList()
        return when {
            uuids.any { it.startsWith("0000111E") || it.startsWith("0000110B") || it.startsWith("0000110A") } -> "🎧 헤드셋/이어폰"
            uuids.any { it.startsWith("00001812") } -> "⌨️ 키보드/마우스"
            uuids.any { it.startsWith("00001800") || it.startsWith("0000180A") } -> "📱 스마트폰/기기"
            uuids.any { it.startsWith("00001810") } -> "💓 헬스케어"
            uuids.any { it.startsWith("00001802") || it.startsWith("00001803") } -> "📡 BLE 센서"
            uuids.any { it.startsWith("00001101") } -> "💻 컴퓨터"
            uuids.isNotEmpty() -> "📡 기타 (${uuids.first().take(8)})"
            else -> Constants.UNKNOWN
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