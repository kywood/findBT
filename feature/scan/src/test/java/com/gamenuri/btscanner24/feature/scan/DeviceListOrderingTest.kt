package com.gamenuri.btscanner24.feature.scan

import com.gamenuri.btscanner24.core.model.BleDevice
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceListOrderingTest {

    private fun device(address: String, name: String? = null, rssi: Int = -70, firstSeen: Long = 0) =
        BleDevice(address = address, name = name, rssi = rssi, firstSeen = firstSeen, lastSeen = firstSeen)

    @Test
    fun `named only filter drops devices without a name`() {
        val result = DeviceListOrdering.filter(
            listOf(device("A", "Buds"), device("B")),
            ScanFilter(namedOnly = true),
        )
        assertEquals(listOf("A"), result.map { it.address })
    }

    @Test
    fun `query matches name or address ignoring case`() {
        val devices = listOf(device("AA:BB", "Galaxy Buds"), device("CC:DD", "Watch"))
        assertEquals(listOf("AA:BB"), DeviceListOrdering.filter(devices, ScanFilter(query = "buds")).map { it.address })
        assertEquals(listOf("CC:DD"), DeviceListOrdering.filter(devices, ScanFilter(query = "cc:")).map { it.address })
    }

    @Test
    fun `rssi sort puts the strongest signal first`() {
        val sorted = DeviceListOrdering.sort(listOf(device("A", rssi = -80), device("B", rssi = -40)), SortType.RSSI)
        assertEquals(listOf("B", "A"), sorted.map { it.address })
    }

    @Test
    fun `locked order keeps existing positions and appends new devices`() {
        val result = DeviceListOrdering.keepOrder(
            previousOrder = listOf("B", "A"),
            devices = listOf(device("A", firstSeen = 1), device("C", firstSeen = 3), device("B", firstSeen = 2)),
        )
        assertEquals(listOf("B", "A", "C"), result.map { it.address })
    }
}
