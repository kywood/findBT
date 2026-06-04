package com.gamenuri.findbt


data class ParsedAdvertiseData(
    val name: String = Constants.UNKNOWN,
    val manufacturerId: String = Constants.UNKNOWN,
    val deviceType: String = Constants.UNKNOWN,
    val txPower: Int? = null,
    val flags: String = Constants.UNKNOWN,
    val serviceUuids: List<String> = emptyList(),
    val rawBytes: String = Constants.UNKNOWN
)

object BleParser {

    fun parse(rawBytes: ByteArray): ParsedAdvertiseData {
        var i = 0
        var name = Constants.UNKNOWN
        var manufacturerId = Constants.UNKNOWN
        var deviceType = Constants.UNKNOWN
        var txPower: Int? = null
        var flags = Constants.UNKNOWN
        val serviceUuids = mutableListOf<String>()

        while (i < rawBytes.size) {
            val length = rawBytes[i].toInt() and 0xFF
            if (length == 0) break
            if (i + 1 >= rawBytes.size) break

            val type = rawBytes[i + 1].toInt() and 0xFF
//            val data = rawBytes.slice(i + 2 until i + 1 + length).toByteArray()

            val dataEnd = (i + 1 + length).coerceAtMost(rawBytes.size)
            val data = rawBytes.slice(i + 2 until dataEnd).toByteArray()

            when (type) {
                // 이름
                0x08, 0x09 -> name = String(data)

                // 제조사 데이터
//                0xFF -> {
//                    if (data.size >= 2) {
//                        val id = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
//                        manufacturerId = getManufacturerById(id)
//                    }
//                }

                0xFF -> {
                    // manufacturerId가 아직 UNKNOWN일 때만 저장하여 덮어쓰기 방지
                    if (data.size >= 2 && manufacturerId == Constants.UNKNOWN) {
                        val id = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
                        manufacturerId = getManufacturerById(id)
                    }
                }

                0x16 -> {
                    if (data.size >= 2 && manufacturerId == Constants.UNKNOWN) {
                        // Service Data의 앞 2바이트는 16-bit UUID입니다.
                        val uuid = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)

                        // 특정 UUID를 통해 제조사나 기기를 유추할 수 있습니다.
                        manufacturerId = when (uuid) {
                            // 🌐 구글 (Google) & 자회사
                            0xFEAA -> "Google (Eddystone Beacon)"
                            0xFE9F, 0xFEA0, 0xFEF4 -> "Google"
                            0xFEAF, 0xFEB0 -> "Nest Labs (Google Smart Home)"

                            // 📱 아시아 테크 & 웨어러블 (주로 주변에서 많이 잡히는 기기들)
                            0xFE95 -> "Xiaomi"
                            0xFEE0 -> "Anhui Huami (Amazfit / Mi Band)" // 미밴드, 젭 등에서 매우 자주 스캔됨
                            0xFD69 -> "Samsung Electronics"
                            0xFEB3 -> "Taobao (Alibaba)"

                            // 🚗 자동차 & 모빌리티
                            0xFE96, 0xFE97 -> "Tesla Motors"

                            // 🛒 쇼핑, 금융 및 엔터프라이즈
                            0xFE03 -> "Amazon"
                            0xFEFA, 0xFEF9 -> "PayPal"
                            0xFEB2 -> "Microsoft"

                            // 📡 비콘(Beacon) 및 IoT 기기
                            0xFCD2 -> "BTHome (Smart Home Open Standard)" // 홈어시스턴트 등 오픈소스 IoT에서 자주 쓰임
                            0xFE9A -> "Estimote (Beacon)"
                            0xFE24 -> "August Home (Smart Lock)"
                            0xFE98, 0xFE99 -> "Currant (Smart Outlet)"

                            // 매칭되지 않으면 기존 값(UNKNOWN) 유지
                            else -> manufacturerId
                        }
                    }
                }

                // Appearance (기기 타입)
                0x19 -> {
                    if (data.size >= 2) {
                        val appearance = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
                        deviceType = getDeviceTypeByAppearance(appearance)
                    }
                }

                // TX Power
                0x0A -> {
                    if (data.isNotEmpty()) {
                        txPower = data[0].toInt().let { if (it > 127) it - 256 else it }
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
                        j += 2
                    }
                }

                // 128-bit Service UUID
                0x06, 0x07 -> {
                    if (data.size >= 16) {
                        val uuid = data.reversed().joinToString("") { "%02X".format(it) }
                        serviceUuids.add("${uuid.substring(0,8)}-${uuid.substring(8,12)}-${uuid.substring(12,16)}-${uuid.substring(16,20)}-${uuid.substring(20)}")
                    }
                }
            }
            i += length + 1
        }

        return ParsedAdvertiseData(
            name = name,
            manufacturerId = manufacturerId,
            deviceType = deviceType,
            txPower = txPower,
            flags = flags,
            serviceUuids = serviceUuids,
            rawBytes = rawBytes.toHex()
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

    private fun getManufacturerById(id: Int): String {
        return when (id) {
            0x004C -> "Apple"
            0x0075 -> "Samsung"
            0x0006 -> "Microsoft"
            0x00E0 -> "Google"
            0x038F -> "Xiaomi"
            0x0171 -> "Huawei"
            0x0078 -> "Bose"
            0x008A -> "Jabra (GN Audio)"
            0x00D7 -> "Sony"
            0x0310 -> "JBL / Harman"
            0x01D6 -> "Sennheiser"
            0x02D0 -> "Philips"
            0x071E -> "QCY (Hele Electronics)"
            0x0BDA -> "Realtek"
            0x0059 -> "Nordic Semiconductor"
            0x0499 -> "Ruuvi Innovations"
            0x000F -> "Broadcom"
            0x0046 -> "MediaTek"
            0x0025 -> "Qualcomm"
            0x0157 -> "Logitech"
            0x01FF -> "LG Electronics"
            0x0117 -> "Lenovo"
            0x02FE -> "OPPO"
            0x07D8 -> "OnePlus"
            else -> "${Constants.UNKNOWN} (0x${"%04X".format(id)})"
        }
    }

    private fun getDeviceTypeByAppearance(appearance: Int): String {
        return when (appearance) {
            0x0040, 0x0041 -> "📱 스마트폰"
            0x0080, 0x0081 -> "💻 컴퓨터"
            0x0180, 0x0181, 0x0182, 0x0183 -> "🎧 헤드셋/이어폰"
            0x00C0, 0x00C1 -> "⌚ 스마트워치"
            0x0100, 0x0101 -> "🏃 피트니스 트래커"
            0x0200, 0x0201, 0x0202 -> "💓 헬스케어"
            0x03C0 -> "⌨️ 키보드"
            0x03C2 -> "🖱️ 마우스"
            0x0300, 0x0301 -> "🎮 게임패드"
            0x0500, 0x0501 -> "📡 BLE 센서"
            else -> Constants.UNKNOWN
        }
    }

    fun ByteArray.toHex(): String = joinToString(":") { "%02X".format(it) }
}