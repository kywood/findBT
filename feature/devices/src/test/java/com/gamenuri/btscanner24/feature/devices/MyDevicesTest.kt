package com.gamenuri.btscanner24.feature.devices

import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.DeviceCategory
import com.gamenuri.btscanner24.core.model.PairedDevice
import com.gamenuri.btscanner24.core.model.SavedDevice
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

    private val keys = SavedDevice("BB:00:00:00:00:01", "My keys", DeviceCategory.UNKNOWN, "Tagly 0006", 5L)

    private fun buildSaved(seen: List<BleDevice>, saved: List<SavedDevice> = listOf(keys)) =
        MyDevices.build(listOf(buds, car), seen, emptySet(), now, saved) { 3.0 }

    @Test
    fun `a saved device that is heard leads its own section with a distance`() {
        val state = buildSaved(listOf(seen(keys.address, null), seen(buds.address, null)))
        val item = state.saved.single()
        assertEquals("My keys", item.paired.name)
        assertTrue(item.isSaved)
        assertEquals(3.0, item.distanceMeters!!, 0.0)
        assertEquals(listOf(buds), state.nearby.map { it.paired })
        assertEquals(1, state.savedCount)
    }

    @Test
    fun `a saved device is recognised by the name it advertised, not the name the user gave`() {
        val moved = buildSaved(listOf(seen("7F:00:00:00:00:09", "tagly 0006")))
        assertEquals("7F:00:00:00:00:09", moved.saved.single().findAddress)
        assertTrue(buildSaved(listOf(seen("7F:00:00:00:00:09", "My keys"))).saved.isEmpty())
    }

    @Test
    fun `a saved device that is not heard waits in not nearby and still counts`() {
        val state = buildSaved(emptyList())
        assertTrue(state.saved.isEmpty())
        assertTrue(state.notNearby.any { it.isSaved && it.paired.name == "My keys" })
        assertEquals(1, state.savedCount)
        assertFalse(state.isEmpty)
    }

    @Test
    fun `saved devices never go to the collapsed group and paired ones show once`() {
        val alsoPaired = keys.copy(address = buds.address)
        val state = buildSaved(emptyList(), listOf(keys, alsoPaired))
        assertTrue(state.others.none { it.isSaved })
        assertEquals(1, state.savedCount)
        assertEquals(1, (state.notNearby + state.nearby).count { it.paired.address == buds.address })
    }
}
