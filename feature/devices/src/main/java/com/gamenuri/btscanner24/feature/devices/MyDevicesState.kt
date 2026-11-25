package com.gamenuri.btscanner24.feature.devices

import com.gamenuri.btscanner24.core.ble.ScanFailure
import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.LastSeen
import com.gamenuri.btscanner24.core.model.PairedDevice
import com.gamenuri.btscanner24.core.model.ProtectionSettings

/** One paired or saved device on the My Devices tab. */
data class MyDeviceItem(
    val paired: PairedDevice,
    /** The live advertisement matched to this paired device, if any was heard this session. */
    val sighting: BleDevice?,
    /** Heard within the last [MyDevices.ACTIVE_WINDOW_MS], or connected to this phone. */
    val isNearby: Boolean,
    val distanceMeters: Double?,
    /** Saved by hand (#108), not paired: its menu removes it instead of hiding it. */
    val isSaved: Boolean = false,
    /** Connected to this phone right now (#119): it is close even when the scan does not hear it. */
    val isConnected: Boolean = false,
    /** When and where it was last heard, kept from earlier sessions too (#162). */
    val lastSeen: LastSeen? = null,
) {
    /** Address to open find mode with: the live (possibly rotated) one when known. */
    val findAddress: String get() = sighting?.address ?: paired.address
}

data class MyDevicesUiState(
    /** Saved by hand and heard right now (#108); shown first. */
    val saved: List<MyDeviceItem> = emptyList(),
    /** Every saved device, nearby or not: what the free limit counts. */
    val savedCount: Int = 0,
    val nearby: List<MyDeviceItem> = emptyList(),
    val notNearby: List<MyDeviceItem> = emptyList(),
    /** Cars, PCs and other paired devices, shown in a collapsed group. */
    val others: List<MyDeviceItem> = emptyList(),
    val hasPermission: Boolean = true,
    val isEmpty: Boolean = false,
    /** The scan cannot run (Bluetooth or Location off…), so "nearby" cannot be known right now. */
    val scanFailure: ScanFailure? = null,
    /** The user turned place recording on (#178); until then last-seen records hold the time only. */
    val recordsPlace: Boolean = false,
    /** Protection mode (#163) is off: the tab offers to turn it on. False until that is known. */
    val protectionOff: Boolean = false,
    /** Devices with a standing left-behind alert. */
    val leftBehind: List<LeftBehind> = emptyList(),
) {
    /** A listed device that may have been left behind, last heard at [lastHeardAt]. */
    data class LeftBehind(val item: MyDeviceItem, val lastHeardAt: Long)

    /** Every device on the tab, whatever its section. */
    val all: List<MyDeviceItem> get() = saved + nearby + notNearby + others

    /** Adds what protection mode says: whether it is off, and the alerts for devices still listed. */
    fun withProtection(settings: ProtectionSettings): MyDevicesUiState {
        val listed = all.associateBy { it.paired.address }
        val alerts = if (settings.enabled) settings.alerts else emptyMap()
        return copy(
            protectionOff = !settings.enabled,
            leftBehind = alerts.mapNotNull { (address, at) -> listed[address]?.let { LeftBehind(it, at) } },
        )
    }

    /** The same lists with each device's kept record, by the address it is listed under. */
    fun withLastSeen(records: Map<String, LastSeen>): MyDevicesUiState {
        if (records.isEmpty()) return this
        fun List<MyDeviceItem>.marked() = map { it.copy(lastSeen = records[it.paired.address]) }
        return copy(
            saved = saved.marked(),
            nearby = nearby.marked(),
            notNearby = notNearby.marked(),
            others = others.marked(),
        )
    }
}
