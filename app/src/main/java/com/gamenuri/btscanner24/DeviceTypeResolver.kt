package com.gamenuri.btscanner24

object DeviceTypeResolver {

    fun resolveByAppearance(appearance: Int): String {
        return when (appearance) {
            0x0040, 0x0041 -> "📱 Smartphone"
            0x0080, 0x0081 -> "💻 Computer"
            0x0180, 0x0181, 0x0182, 0x0183 -> "🎧 Headset/Earphone"
            0x00C0, 0x00C1 -> "⌚ Smartwatch"
            0x0100, 0x0101 -> "🏃 Fitness Tracker"
            0x0200, 0x0201, 0x0202 -> "💓 Healthcare"
            0x03C0 -> "⌨️ Keyboard"
            0x03C2 -> "🖱️ Mouse"
            0x0300, 0x0301 -> "🎮 Gamepad"
            0x0500, 0x0501 -> "📡 BLE Sensor"
            else -> Constants.UNKNOWN
        }
    }

    fun resolveByServiceUuid(uuids: List<String>): String {
        return when {
            uuids.any { it.startsWith("0000111E") || it.startsWith("0000110B") || it.startsWith("0000110A") } -> "🎧 Headset/Earphone"
            uuids.any { it.startsWith("00001812") } -> "⌨️ Keyboard/Mouse"
            uuids.any { it.startsWith("00001800") || it.startsWith("0000180A") } -> "📱 Smartphone/Device"
            uuids.any { it.startsWith("00001810") } -> "💓 Healthcare"
            uuids.any { it.startsWith("00001802") || it.startsWith("00001803") } -> "📡 BLE Sensor"
            uuids.any { it.startsWith("00001101") } -> "💻 Computer"
            else -> Constants.UNKNOWN
        }
    }
}