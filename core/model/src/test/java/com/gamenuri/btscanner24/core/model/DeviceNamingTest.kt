package com.gamenuri.btscanner24.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceNamingTest {

    private fun device(
        name: String? = null,
        modelName: String? = null,
        manufacturer: String? = null,
        category: DeviceCategory = DeviceCategory.UNKNOWN,
        address: String = "AA:BB:CC:DD:3F:2A",
    ) = BleDevice(
        address = address,
        name = name,
        modelName = modelName,
        rssi = -60,
        firstSeen = 0,
        lastSeen = 0,
        manufacturer = manufacturer,
        category = category,
    )

    @Test
    fun `paired alias wins over everything advertised`() {
        val label = DeviceNaming.label(device(name = "Buds2", modelName = "AirPods Pro"), pairedAlias = "wood's Buds2")
        assertEquals(DeviceLabel.Named("wood's Buds2", NameSource.PAIRED_ALIAS, "3F2A"), label)
    }

    @Test
    fun `user name comes before the decoded model`() {
        val label = DeviceNaming.label(device(modelName = "AirPods Pro"), userName = "Mom's AirPods")
        assertEquals(NameSource.USER, (label as DeviceLabel.Named).source)
    }

    @Test
    fun `decoded model comes before the advertised name`() {
        val label = DeviceNaming.label(device(name = "Pods", modelName = "AirPods Pro 2"))
        assertEquals("AirPods Pro 2", (label as DeviceLabel.Named).text)
        assertEquals(NameSource.MODEL, label.source)
    }

    @Test
    fun `no name gives maker, type and address suffix instead of Unknown`() {
        val label = DeviceNaming.label(
            device(name = "Unknown", manufacturer = "Samsung", category = DeviceCategory.EARBUDS),
        )
        assertEquals(DeviceLabel.Generic("Samsung", DeviceCategory.EARBUDS, "3F2A"), label)
    }

    @Test
    fun `unnamed Apple Find My signal is marked, a named one is not`() {
        val tracker = device(manufacturer = "Apple", category = DeviceCategory.TRACKER).copy(companyId = 0x004C)
        assertTrue((DeviceNaming.label(tracker) as DeviceLabel.Generic).appleFindMy)
        assertTrue(DeviceNaming.label(tracker, userName = "Keys") is DeviceLabel.Named)
        val other = device(manufacturer = "Tile", category = DeviceCategory.TRACKER).copy(companyId = 0x00C7)
        assertFalse((DeviceNaming.label(other) as DeviceLabel.Generic).appleFindMy)
    }

    @Test
    fun `blank alias and blank name are ignored`() {
        val label = DeviceNaming.label(device(name = "  "), pairedAlias = " ")
        assertTrue(label is DeviceLabel.Generic)
    }

    @Test
    fun `address suffix is the last four hex digits`() {
        assertEquals("EEFF", DeviceNaming.addressSuffix("aa:bb:cc:dd:ee:ff"))
    }

    @Test
    fun `same name twice is flagged as duplicate, case insensitive`() {
        val a = DeviceNaming.label(device(name = "MI_SCALE", address = "00:00:00:00:00:01"))
        val b = DeviceNaming.label(device(name = "mi_scale", address = "00:00:00:00:00:02"))
        val c = DeviceNaming.label(device(name = "hiot0"))
        val duplicates = DeviceNaming.duplicateNames(listOf(a, b, c))
        assertTrue(DeviceNaming.isDuplicate(a, duplicates))
        assertTrue(DeviceNaming.isDuplicate(b, duplicates))
        assertFalse(DeviceNaming.isDuplicate(c, duplicates))
    }
}
