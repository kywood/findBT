package com.gamenuri.btscanner24.core.ble

import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.DeviceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressRotationTest {

    private fun buds(address: String, lastSeen: Long, name: String? = "Galaxy Buds2", companyId: Int? = 0x0075) =
        BleDevice(
            address = address,
            name = name,
            rssi = -60,
            firstSeen = 0,
            lastSeen = lastSeen,
            companyId = companyId,
            category = DeviceCategory.EARBUDS,
            serviceUuids16 = listOf(0xFD69),
        )

    @Test
    fun `a silent row with the same name and data is the same device`() {
        val old = buds("AA", lastSeen = 1_000)
        assertEquals(old, AddressRotation.predecessor(buds("BB", lastSeen = 6_000), listOf(old), now = 6_000))
    }

    @Test
    fun `two units of the same model that are both advertising stay apart`() {
        val other = buds("AA", lastSeen = 5_500)
        assertNull(AddressRotation.predecessor(buds("BB", lastSeen = 6_000), listOf(other), now = 6_000))
    }

    @Test
    fun `devices without a name are never merged`() {
        val old = buds("AA", lastSeen = 0, name = null)
        assertNull(AddressRotation.predecessor(buds("BB", lastSeen = 9_000, name = null), listOf(old), now = 9_000))
    }

    @Test
    fun `a different maker under the same name is another device`() {
        val old = buds("AA", lastSeen = 0, companyId = 0x004C)
        assertNull(AddressRotation.predecessor(buds("BB", lastSeen = 9_000), listOf(old), now = 9_000))
    }

    @Test
    fun `a different name is another device`() {
        val old = buds("AA", lastSeen = 0, name = "Galaxy Buds Pro")
        assertNull(AddressRotation.predecessor(buds("BB", lastSeen = 9_000), listOf(old), now = 9_000))
    }

    @Test
    fun `the most recently heard of several silent rows is taken`() {
        val older = buds("AA", lastSeen = 1_000)
        val newer = buds("CC", lastSeen = 3_000)
        val found = AddressRotation.predecessor(buds("BB", lastSeen = 9_000), listOf(older, newer), now = 9_000)
        assertEquals("CC", found?.address)
    }

    @Test
    fun `its own row is not a predecessor`() {
        val same = buds("BB", lastSeen = 0)
        assertNull(AddressRotation.predecessor(buds("BB", lastSeen = 9_000), listOf(same), now = 9_000))
    }
}
