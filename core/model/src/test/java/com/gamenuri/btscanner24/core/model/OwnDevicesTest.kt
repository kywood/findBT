package com.gamenuri.btscanner24.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnDevicesTest {
    private val buds = PairedDevice("AA:00:00:00:00:01", "Min's buds", DeviceCategory.EARBUDS, "Galaxy Buds FE")
    private val car = PairedDevice("AA:00:00:00:00:02", "My Car", DeviceCategory.UNKNOWN)
    private val signature = AdvertSignature(0x0157, setOf(0xFE95), DeviceCategory.TRACKER)
    private val keys = SavedDevice("BB:00:00:00:00:01", "My keys", DeviceCategory.TRACKER, "Tagly 0006", 5L, signature)
    private val budsSaved = SavedDevice(buds.address, "Buds", DeviceCategory.EARBUDS, "Galaxy Buds FE", 6L)

    private fun seen(address: String, name: String?, lastSeen: Long = 0) =
        BleDevice(address = address, name = name, rssi = -60, firstSeen = 0, lastSeen = lastSeen)

    private val own = OwnDevices.list(listOf(buds, car), listOf(keys, budsSaved), hidden = setOf(car.address))

    @Test
    fun `lists paired devices that are not hidden, then saved ones, each once`() {
        assertEquals(listOf(buds.address, keys.address), own.map { it.device.address })
        assertEquals(listOf(false, true), own.map { it.isSaved })
        assertEquals(listOf("Min's buds", "Galaxy Buds FE"), own.first().matchNames)
        assertEquals(signature, own.last().signature)
    }

    @Test
    fun `a sighting is found by the listed address first, then by the newest one with a matching name`() {
        val sightings = OwnDevices.Sightings(
            listOf(
                seen("7F:00:00:00:00:01", " galaxy buds fe", lastSeen = 5),
                seen("7F:00:00:00:00:02", "Galaxy Buds FE", lastSeen = 9),
                seen(keys.address, null, lastSeen = 1),
                seen("7F:00:00:00:00:03", "Tagly 0006", lastSeen = 7),
            ),
        )
        assertEquals("7F:00:00:00:00:02", sightings.of(own.first())?.address)
        assertEquals(keys.address, sightings.of(own.last())?.address)
        assertNull(OwnDevices.Sightings(listOf(seen("7F:00:00:00:00:04", "My keys"))).of(own.last()))
    }

    @Test
    fun `a paired device is certain by name, a saved one only by address or signature`() {
        val (paired, saved) = own
        assertTrue(OwnDevices.isCertain(paired, seen("7F:00:00:00:00:01", "Galaxy Buds FE")))
        assertTrue(OwnDevices.isCertain(saved, seen(keys.address, null)))
        assertFalse(OwnDevices.isCertain(saved, seen("7F:00:00:00:00:03", "Tagly 0006")))
        val sameAdvert = seen("7F:00:00:00:00:03", "Tagly 0006")
            .copy(companyId = 0x0157, serviceUuids16 = listOf(0xFE95), category = DeviceCategory.TRACKER)
        assertTrue(OwnDevices.isCertain(saved, sameAdvert))
        assertFalse(OwnDevices.isCertain(saved.copy(signature = null), sameAdvert))
    }
}
