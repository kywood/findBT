package com.gamenuri.btscanner24.core.ble

import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.DeviceCategory

/**
 * Recognises the same physical device after it changes its random MAC address (most earbuds and
 * phones rotate it every ~15 minutes for privacy). Used by find mode and My Devices.
 *
 * Matching uses what stays stable across rotations: name, SIG company ID, 16-bit service UUIDs and
 * category. It is a heuristic: two identical earbuds from the same brand can match each other,
 * so callers only switch to a new address when the old one has gone quiet.
 */
data class DeviceFingerprint(
    val name: String?,
    val companyId: Int?,
    val serviceUuids16: Set<Int>,
    val category: DeviceCategory,
) {
    /** 0.0 (unrelated) to 1.0 (everything stable matches). */
    fun similarity(other: DeviceFingerprint): Double {
        var score = 0.0
        var weight = 0.0
        fun add(w: Double, matches: Boolean?) {
            if (matches == null) return
            weight += w
            if (matches) score += w
        }
        add(0.45, if (name.isNullOrBlank() || other.name.isNullOrBlank()) null else name.equals(other.name, true))
        add(0.25, if (companyId == null || other.companyId == null) null else companyId == other.companyId)
        val bothHaveUuids = serviceUuids16.isNotEmpty() && other.serviceUuids16.isNotEmpty()
        add(0.2, if (bothHaveUuids) serviceUuids16 == other.serviceUuids16 else null)
        add(
            0.1,
            if (category == DeviceCategory.UNKNOWN || other.category == DeviceCategory.UNKNOWN) {
                null
            } else {
                category == other.category
            },
        )
        // A single matching weak field (e.g. only "Apple") must not count as the same device.
        if (weight < 0.45) return 0.0
        return score / weight
    }

    companion object {
        const val SAME_DEVICE_THRESHOLD = 0.85

        fun of(device: BleDevice) = DeviceFingerprint(
            name = device.name?.takeIf { device.hasName },
            companyId = device.companyId,
            serviceUuids16 = device.serviceUuids16.toSet(),
            category = device.category,
        )
    }
}

/**
 * Follows one target device through MAC rotations. Feed every scanned device; [isTarget] says
 * whether a sighting belongs to the target and adopts a new address when the current one is silent.
 */
class TargetTracker(initial: BleDevice, private val silenceBeforeSwitchMs: Long = 3_000) {
    var address: String = initial.address
        private set
    private val fingerprint = DeviceFingerprint.of(initial)
    private var lastSeen: Long = initial.lastSeen

    /** How many times the target moved to a new address (reported in the prototype measurements). */
    var addressSwitches: Int = 0
        private set

    fun isTarget(device: BleDevice, now: Long): Boolean {
        if (device.address == address) {
            lastSeen = now
            return true
        }
        val silent = now - lastSeen > silenceBeforeSwitchMs
        if (silent && fingerprint.similarity(DeviceFingerprint.of(device)) >= DeviceFingerprint.SAME_DEVICE_THRESHOLD) {
            address = device.address
            lastSeen = now
            addressSwitches++
            return true
        }
        return false
    }
}
