package com.gamenuri.btscanner24.core.ble

import com.gamenuri.btscanner24.core.model.Constants
import com.gamenuri.btscanner24.core.model.DeviceCategory

data class ParsedAdvertiseData(
    val name: String = Constants.UNKNOWN,
    /** Company name, or null when the packet does not identify one. */
    val manufacturer: String? = null,
    /** Raw Bluetooth SIG company ID from manufacturer-specific data, even if not in the table. */
    val companyId: Int? = null,
    val category: DeviceCategory = DeviceCategory.UNKNOWN,
    /** 16-bit service UUIDs as numbers, for category and feature checks (e.g. 0x1802). */
    val serviceUuids16: List<Int> = emptyList(),
    val txPower: Int? = null,
    val flags: String = Constants.UNKNOWN,
    val serviceUuids: List<String> = emptyList(),
    val rawBytes: String = Constants.UNKNOWN,
)

object BleParser {

    fun parse(rawBytes: ByteArray, companies: CompanyDirectory): ParsedAdvertiseData {
        var i = 0
        var name = Constants.UNKNOWN
        var companyId: Int? = null
        var serviceOwner: String? = null
        var appearanceCategory = DeviceCategory.UNKNOWN
        var vendorCategory = DeviceCategory.UNKNOWN
        val serviceUuids16 = mutableListOf<Int>()
        var txPower: Int? = null
        var flags = Constants.UNKNOWN
        val serviceUuids = mutableListOf<String>()

        while (i < rawBytes.size) {
            val length = rawBytes[i].toInt() and 0xFF

            // 루프 탈출 조건 조건 강화 (패딩 검사 및 인덱스 오버플로우 방지)
            if (length == 0) break
            if (i + 1 >= rawBytes.size) break

            val type = rawBytes[i + 1].toInt() and 0xFF

            // [버그 수정]: slice 레인지 계산 실수와 낭비를 줄이기 위해 copyOfRange 사용
            // i+2(데이터 시작)부터 i+1+length(데이터 끝 바로 다음 인덱스)까지 정확히 추출
            val dataEnd = minOf(i + 1 + length, rawBytes.size)
            val data = rawBytes.copyOfRange(i + 2, dataEnd)

            when (type) {
                // 이름 (UTF_8 지정 및 공백 제거)
                0x08, 0x09 -> name = String(data, Charsets.UTF_8).trim()

                // 제조사 데이터
                0xFF -> {
                    if (data.size >= 2 && companyId == null) {
                        val id = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
                        companyId = id
                        vendorCategory = DeviceCategoryResolver.fromManufacturerData(id, data.copyOfRange(2, data.size))
                    }
                }

                // Service Data (16-bit UUID) → 제조사 추정 fallback
                0x16 -> {
                    if (data.size >= 2) {
                        val uuid = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
                        if (uuid !in serviceUuids16) serviceUuids16.add(uuid)
                        if (serviceOwner == null) serviceOwner = companies.memberServiceOwner(uuid)
                    }
                }

                // Appearance (기기 타입)
                0x19 -> {
                    if (data.size >= 2) {
                        val appearance = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
                        appearanceCategory = DeviceCategoryResolver.fromAppearance(appearance)
                    }
                }

                // TX Power (Byte 캐스팅만으로 음수 자동 보정)
                0x0A -> {
                    if (data.isNotEmpty()) {
                        txPower = data[0].toInt()
                    }
                }

                // Flags
                0x01 -> {
                    if (data.isNotEmpty()) {
                        flags = parseFlagsString(data[0].toInt())
                    }
                }

                // 16-bit Service UUID
                0x02, 0x03 -> {
                    var j = 0
                    while (j + 1 < data.size) {
                        val uuid = ((data[j + 1].toInt() and 0xFF) shl 8) or (data[j].toInt() and 0xFF)
                        serviceUuids.add("0x${"%04X".format(uuid)}")
                        serviceUuids16.add(uuid)
                        if (serviceOwner == null) serviceOwner = companies.memberServiceOwner(uuid)
                        j += 2
                    }
                }

                // 128-bit Service UUID
                0x06, 0x07 -> {
                    if (data.size >= 16) {
                        val uuid = data.reversedArray().joinToString("") { "%02X".format(it) }
                        serviceUuids.add(
                            "${uuid.substring(0, 8)}-${uuid.substring(8, 12)}-" +
                                "${uuid.substring(12, 16)}-${uuid.substring(16, 20)}-" +
                                "${uuid.substring(20)}",
                        )
                    }
                }
            }
            i += length + 1
        }

        val category = DeviceCategoryResolver.firstKnown(
            { appearanceCategory },
            { vendorCategory },
            { DeviceCategoryResolver.fromServiceUuids(serviceUuids16) },
            { DeviceCategoryResolver.fromName(name.takeIf { it != Constants.UNKNOWN }) },
        )

        return ParsedAdvertiseData(
            name = name,
            // Manufacturer data names the maker directly; a member service UUID is only a hint.
            manufacturer = companyId?.let(companies::companyName) ?: serviceOwner,
            companyId = companyId,
            category = category,
            serviceUuids16 = serviceUuids16,
            txPower = txPower,
            flags = flags,
            serviceUuids = serviceUuids,
            rawBytes = rawBytes.toHex(),
        )
    }

    private fun parseFlagsString(flags: Int): String {
        val result = mutableListOf<String>()
        if (flags and 0x01 != 0) result.add("LE Limited")
        if (flags and 0x02 != 0) result.add("LE General")
        if (flags and 0x04 != 0) result.add("BR/EDR Off")
        if (flags and 0x08 != 0) result.add("BR/EDR")
        return if (result.isEmpty()) Constants.UNKNOWN else result.joinToString(", ")
    }

    fun ByteArray.toHex(): String = joinToString(":") { "%02X".format(it) }
}
