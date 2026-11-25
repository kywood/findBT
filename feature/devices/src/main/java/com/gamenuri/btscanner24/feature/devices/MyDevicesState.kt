package com.gamenuri.btscanner24.feature.devices

import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.PairedDevice

/** One paired or saved device on the My Devices tab. */
data class MyDeviceItem(
    val paired: PairedDevice,
    /** The live advertisement matched to this paired device, if any was heard this session. */
    val sighting: BleDevice?,
    /** Heard within the last [MyDevices.ACTIVE_WINDOW_MS]. */
    val isNearby: Boolean,
    val distanceMeters: Double?,
    /** Saved by hand (#108), not paired: its menu removes it instead of hiding it. */
    val isSaved: Boolean = false,
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
)
