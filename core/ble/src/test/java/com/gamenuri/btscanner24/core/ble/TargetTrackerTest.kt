package com.gamenuri.btscanner24.core.ble

import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.DeviceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetTrackerTest {

    private fun buds(address: String, lastSeen: Long = 0, name: String? = "Galaxy Buds2") = BleDevice(
        address = address,
        name = name,
        rssi = -60,
        firstSeen = 0,
        lastSeen = lastSeen,
        companyId = 0x0075,
        category = DeviceCategory.EARBUDS,
        serviceUuids16 = listOf(0xFD69),
    )

    @Test
    fun `new random address with the same fingerprint is adopted after the old one goes quiet`() {
        val tracker = TargetTracker(buds("AA"))
        assertTrue(tracker.isTarget(buds("AA"), 1_000))
        // Rotation: old address silent for more than 3 s.
        assertTrue(tracker.isTarget(buds("BB"), 5_000))
        assertEquals("BB", tracker.address)
        assertEquals(1, tracker.addressSwitches)
    }

    @Test
    fun `look-alike is ignored while the target is still being heard`() {
        val tracker = TargetTracker(buds("AA"))
        tracker.isTarget(buds("AA"), 1_000)
        assertFalse(tracker.isTarget(buds("BB"), 2_000))
        assertEquals("AA", tracker.address)
    }

    @Test
    fun `different device is never adopted`() {
        val tracker = TargetTracker(buds("AA"))
        assertFalse(tracker.isTarget(buds("CC", name = "WH-1000XM5").copy(companyId = 0x012D), 10_000))
    }

    @Test
    fun `manufacturer alone is not enough`() {
        val a = DeviceFingerprint("", 0x004C, emptySet(), DeviceCategory.UNKNOWN)
        val b = DeviceFingerprint(null, 0x004C, emptySet(), DeviceCategory.UNKNOWN)
        assertEquals(0.0, a.similarity(b), 0.0)
    }
}
