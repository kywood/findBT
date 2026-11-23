package com.gamenuri.btscanner24.core.ble

import com.gamenuri.btscanner24.core.model.DeviceCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceCategoryResolverTest {

    private val companies = TestCompanyDirectory.instance

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `16-bit service uuid list now resolves (was always Unknown)`() {
        // HID over GATT 0x1812
        assertEquals(DeviceCategory.KEYBOARD, BleParser.parse(bytes(0x03, 0x03, 0x12, 0x18), companies).category)
    }

    @Test
    fun `immediate alert and link loss mean tracker, not sensor`() {
        assertEquals(DeviceCategory.TRACKER, DeviceCategoryResolver.fromServiceUuids(listOf(0x1802)))
        assertEquals(DeviceCategory.TRACKER, DeviceCategoryResolver.fromServiceUuids(listOf(0x1803)))
    }

    @Test
    fun `appearance uses SIG categories`() {
        assertEquals(DeviceCategory.WATCH, DeviceCategoryResolver.fromAppearance(0x00C1))
        assertEquals(DeviceCategory.EARBUDS, DeviceCategoryResolver.fromAppearance(0x0941))
        assertEquals(DeviceCategory.TRACKER, DeviceCategoryResolver.fromAppearance(0x0200))
        assertEquals(DeviceCategory.MOUSE, DeviceCategoryResolver.fromAppearance(0x03C2))
    }

    @Test
    fun `apple proximity pairing is earbuds`() {
        val airpods = BleParser.parse(bytes(0x05, 0xFF, 0x4C, 0x00, 0x07, 0x19), companies)
        assertEquals(DeviceCategory.EARBUDS, airpods.category)
    }

    @Test
    fun `name patterns are the last fallback`() {
        assertEquals(DeviceCategory.EARBUDS, DeviceCategoryResolver.fromName("Galaxy Buds2 Pro"))
        assertEquals(DeviceCategory.WATCH, DeviceCategoryResolver.fromName("Galaxy Watch6"))
        assertEquals(DeviceCategory.TRACKER, DeviceCategoryResolver.fromName("Tile Mate"))
        assertEquals(DeviceCategory.HEADPHONES, DeviceCategoryResolver.fromName("WH-1000XM5"))
        assertEquals(DeviceCategory.UNKNOWN, DeviceCategoryResolver.fromName(null))
    }

    @Test
    fun `car only matches the whole word`() {
        assertEquals(DeviceCategory.CAR, DeviceCategoryResolver.fromName("My Car"))
        assertEquals(DeviceCategory.CAR, DeviceCategoryResolver.fromName("CAR-1234"))
        assertEquals(DeviceCategory.CAR, DeviceCategoryResolver.fromName("Uconnect 2F1A"))
        assertEquals(DeviceCategory.UNKNOWN, DeviceCategoryResolver.fromName("Smart Card Reader"))
        assertEquals(DeviceCategory.UNKNOWN, DeviceCategoryResolver.fromName("Oscar"))
    }
}
