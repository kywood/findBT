package com.gamenuri.btscanner24.feature.protection

import com.gamenuri.btscanner24.core.ble.WatchTarget
import com.gamenuri.btscanner24.core.model.OwnDevices
import com.gamenuri.btscanner24.core.model.PairedDevice
import com.gamenuri.btscanner24.core.model.ProtectionSettings
import com.gamenuri.btscanner24.core.model.SavedDevice

/** One of the user's devices protection mode watches (#163). */
internal data class WatchedDevice(
    /** As listed on My devices: the paired or saved address. */
    val address: String,
    /** What the user calls it; null when it has no name at all. */
    val name: String?,
    /** The names it may advertise under another address, as advertised. */
    val advertisedNames: Set<String>,
)

/**
 * Which devices to listen for and which of them a scan result belongs to. The devices and their
 * names come from [OwnDevices], like everywhere else: the listed address first, then the advertised
 * name, because earbuds and watches advertise from a random address. A same-named device of someone
 * nearby can therefore pass for the user's own; here that can only keep an alert from being raised,
 * never raise one.
 */
internal object WatchList {

    /**
     * The watched devices that are still on My devices; a device that is both paired and saved counts
     * once. Saved devices are watched unless switched off, paired ones only when switched on.
     */
    fun build(settings: ProtectionSettings, saved: List<SavedDevice>, paired: List<PairedDevice>): List<WatchedDevice> {
        val savedAddresses = saved.mapTo(HashSet()) { it.address }
        return own(saved, paired).filter { settings.watches(it.address, isSaved = it.address in savedAddresses) }
    }

    /** Every device of the user's, watched or not: what unknown-tracker detection (#79) must not report. */
    fun own(saved: List<SavedDevice>, paired: List<PairedDevice>): List<WatchedDevice> =
        OwnDevices.list(paired, saved).map { own ->
            WatchedDevice(
                address = own.device.address,
                name = own.device.name?.takeIf { it.isNotBlank() },
                advertisedNames = own.matchNames.filterTo(LinkedHashSet()) { it.isNotBlank() },
            )
        }

    /** What the filtered scan asks Android for: each address and each name on its own. */
    fun targets(devices: List<WatchedDevice>): List<WatchTarget> = devices.flatMap { device ->
        listOf(WatchTarget(device.address, null)) + device.advertisedNames.map { WatchTarget(null, it) }
    }

    /** The watched device a scan result from [address] advertising [advertisedName] belongs to. */
    fun match(devices: List<WatchedDevice>, address: String, advertisedName: String?): WatchedDevice? {
        devices.firstOrNull { it.address == address }?.let { return it }
        val name = advertisedName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return devices.firstOrNull { device ->
            device.advertisedNames.any { it.trim().equals(name, ignoreCase = true) }
        }
    }
}
