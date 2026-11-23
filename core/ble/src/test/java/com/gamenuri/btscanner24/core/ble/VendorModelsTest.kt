package com.gamenuri.btscanner24.core.ble

import com.gamenuri.btscanner24.core.model.DeviceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VendorModelsTest {

    private val companies = TestCompanyDirectory.instance

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `apple proximity pairing decodes the AirPods model`() {
        // len 0x08, type 0xFF, company 0x004C, [0x07 proximity pairing][len 0x19][prefix 0x01][0x14 0x20]
        val parsed = BleParser.parse(bytes(0x08, 0xFF, 0x4C, 0x00, 0x07, 0x19, 0x01, 0x14, 0x20), companies)
        assertEquals("AirPods Pro 2", parsed.modelName)
        assertEquals(DeviceCategory.EARBUDS, parsed.category)
    }

    @Test
    fun `unknown model code and other companies give no model`() {
        assertNull(VendorModels.fromManufacturerData(0x004C, bytes(0x07, 0x19, 0x01, 0x99, 0x99)))
        assertNull(VendorModels.fromManufacturerData(0x0075, bytes(0x07, 0x19, 0x01, 0x14, 0x20)))
    }

    @Test
    fun `truncated apple payload is ignored`() {
        assertNull(VendorModels.fromManufacturerData(0x004C, bytes(0x07, 0x19)))
    }

    @Test
    fun `tv and health names seen on real devices get a category`() {
        assertEquals(DeviceCategory.TV, DeviceCategoryResolver.fromName("MiKetv11"))
        assertEquals(DeviceCategory.TV, DeviceCategoryResolver.fromName("LG_TV"))
        assertEquals(DeviceCategory.HEALTH, DeviceCategoryResolver.fromName("MI_SCALE"))
        assertEquals(DeviceCategory.UNKNOWN, DeviceCategoryResolver.fromName("hiot0"))
    }
}
