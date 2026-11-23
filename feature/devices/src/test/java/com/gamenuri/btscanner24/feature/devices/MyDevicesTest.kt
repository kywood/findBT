package com.gamenuri.btscanner24.feature.devices

import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.DeviceCategory
import com.gamenuri.btscanner24.core.model.PairedDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyDevicesTest {

    private val now = 100_000L
    private val buds = PairedDevice("AA:00:00:00:00:01", "Galaxy Buds2 Pro", DeviceCategory.EARBUDS)
    private val watch = PairedDevice("AA:00:00:00:00:02", "Galaxy Watch6", DeviceCategory.WATCH)
    private val car = PairedDevice("AA:00:00:00:00:03", "My Car", DeviceCategory.UNKNOWN)

    private fun seen(address: String, name: String?, lastSeen: Long = now) =
        BleDevice(address = address, name = name, rssi = -60, firstSeen = 0, lastSeen = lastSeen)

    private fun build(seen: List<BleDevice>, hidden: Set<String> = emptySet()) =
        MyDevices.build(listOf(buds, watch, car), seen, hidden, now) { 2.0 }

    @Test
    fun `matches by paired address`() {
        val state = build(listOf(seen(buds.address, null)))
        assertEquals(listOf(buds), state.nearby.map { it.paired })
        assertEquals(2.0, state.nearby.single().distanceMeters!!, 0.0)
    }

    @Test
    fun `falls back to the name for random BLE addresses`() {
        val state = build(listOf(seen("7F:11:22:33:44:55", "galaxy buds2 pro ")))
        val item = state.nearby.single()
        assertEquals(buds, item.paired)
        assertEquals("7F:11:22:33:44:55", item.findAddress)
    }

    @Test
    fun `not heard recently is not nearby`() {
        val state = build(listOf(seen(watch.address, "Galaxy Watch6", lastSeen = now - 60_000)))
        assertTrue(state.nearby.isEmpty())
        assertTrue(state.notNearby.first().sighting != null)
        assertFalse(state.notNearby.first().isNearby)
    }

    @Test
    fun `cars and computers go to the other group`() {
        val state = build(emptyList())
        assertEquals(listOf(car), state.others.map { it.paired })
        assertEquals(2, state.notNearby.size)
    }

    @Test
    fun `hidden devices are left out`() {
        val state = build(emptyList(), hidden = setOf(buds.address))
        assertFalse(state.notNearby.any { it.paired == buds })
    }

    @Test
    fun `no paired devices is empty`() {
        assertTrue(MyDevices.build(emptyList(), emptyList(), emptySet(), now) { null }.isEmpty)
    }
}
