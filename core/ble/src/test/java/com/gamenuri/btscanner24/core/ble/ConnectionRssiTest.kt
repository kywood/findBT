package com.gamenuri.btscanner24.core.ble

import com.gamenuri.btscanner24.core.model.DeviceCategory
import com.gamenuri.btscanner24.core.model.PairedDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ConnectionRssiTest {
    private var now = 1_000L
    private val repository = DeviceRepository(TestCompanyDirectory.instance) { now }
    private val buds = PairedDevice("AA:00:00:00:00:01", "My Buds", DeviceCategory.EARBUDS)

    @Test
    fun `a link reading adds the connected device like a sighting`() {
        repository.onConnectionRssi(buds, -58)
        val device = repository.devices.value.getValue(buds.address)
        assertEquals("My Buds", device.name)
        assertEquals(DeviceCategory.EARBUDS, device.category)
        assertEquals(-58, device.rssi)
        assertEquals(1_000L, device.lastSeen)
        assertNotNull(device.distanceMeters)
    }

    @Test
    fun `later readings keep the row and move its last seen time`() {
        repository.onConnectionRssi(buds, -58)
        now = 3_000L
        repository.onConnectionRssi(buds, -70)
        val device = repository.devices.value.values.single()
        assertEquals(1_000L, device.firstSeen)
        assertEquals(3_000L, device.lastSeen)
        assertEquals(-70, device.rssi)
    }
}
