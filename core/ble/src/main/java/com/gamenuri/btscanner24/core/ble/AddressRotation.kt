package com.gamenuri.btscanner24.core.ble

import com.gamenuri.btscanner24.core.model.BleDevice

/**
 * Finds the row a device had before it changed its random address (#115). Most earbuds, watches
 * and phones change it every ~15 minutes; without this the list keeps the old row and adds a new
 * one, so the same device shows up several times.
 *
 * A row counts as the same device only when it carries the same name, nothing else that is stable
 * contradicts it (maker, services, kind), and the old address has been silent for
 * [SILENCE_MS]. Two units of the same model that are both in range keep advertising, so
 * neither is silent and they stay two rows. Devices without a name are never merged: there is not
 * enough to tell them apart.
 */
object AddressRotation {
    /** Longer than any usual advertising interval, far shorter than the time between rotations. */
    const val SILENCE_MS = 4_000L

    /** The newest silent row in [known] that [device] continues, or null. */
    fun predecessor(device: BleDevice, known: Collection<BleDevice>, now: Long): BleDevice? {
        if (!device.hasName) return null
        val fingerprint = DeviceFingerprint.of(device)
        return known
            .asSequence()
            .filter { it.address != device.address && it.hasName }
            .filter { it.name.equals(device.name, ignoreCase = true) }
            .filter { now - it.lastSeen >= SILENCE_MS }
            .filter { fingerprint.similarity(DeviceFingerprint.of(it)) >= DeviceFingerprint.SAME_DEVICE_THRESHOLD }
            .maxByOrNull { it.lastSeen }
    }
}
