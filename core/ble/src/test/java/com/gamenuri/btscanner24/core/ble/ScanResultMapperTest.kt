package com.gamenuri.btscanner24.core.ble

import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.Constants
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanResultMapperTest {

    private fun device(
        name: String? = null,
        manufacturer: String? = null,
        deviceType: String = Constants.UNKNOWN,
        rssi: Int = -70,
    ) = BleDevice(
        address = "AA:BB",
        name = name,
        rssi = rssi,
        firstSeen = 0,
        lastSeen = 0,
        manufacturer = manufacturer,
        deviceType = deviceType,
    )

    @Test
    fun `packet without manufacturer keeps the one found earlier`() {
        val first = device(manufacturer = "Apple", deviceType = "Headset")
        val merged = ScanResultMapper.mergeKnown(first, device(name = "AirPods", rssi = -50))
        assertEquals("Apple", merged.manufacturer)
        assertEquals("Headset", merged.deviceType)
        assertEquals("AirPods", merged.name)
        assertEquals(-50, merged.rssi)
    }

    @Test
    fun `newer known values replace older ones`() {
        val merged = ScanResultMapper.mergeKnown(device(manufacturer = "Old"), device(manufacturer = "New"))
        assertEquals("New", merged.manufacturer)
    }
}
