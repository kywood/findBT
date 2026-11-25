package com.gamenuri.btscanner24.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedDeviceTest {
    private val keys = SavedDevice("AA:00:00:00:00:01", "My keys", DeviceCategory.TRACKER, "Tagly 0006", 1_000L)
    private val bag = SavedDevice("AA:00:00:00:00:02", "Bag", DeviceCategory.UNKNOWN, null, 2_000L)

    @Test
    fun `the first save is free, the second needs premium`() {
        assertTrue(SaveLimit.canSaveAnother(savedCount = 0, isPremium = false))
        assertFalse(SaveLimit.canSaveAnother(savedCount = 1, isPremium = false))
        assertFalse(SaveLimit.canSaveAnother(savedCount = 5, isPremium = false))
    }

    @Test
    fun `premium and a day pass have no limit`() {
        assertTrue(SaveLimit.canSaveAnother(savedCount = 1, isPremium = true))
        assertTrue(SaveLimit.canSaveAnother(savedCount = 40, isPremium = true))
    }

    @Test
    fun `only devices that advertise a name can be saved`() {
        val named = BleDevice(address = "AA", name = "Tagly 0006", rssi = -60, firstSeen = 0, lastSeen = 0)
        assertTrue(SaveLimit.canBeSaved(named))
        assertFalse(SaveLimit.canBeSaved(named.copy(name = null)))
        assertFalse(SaveLimit.canBeSaved(named.copy(name = " ")))
    }

    @Test
    fun `storage text round-trips`() {
        assertEquals(listOf(keys, bag), SavedDeviceCodec.decode(SavedDeviceCodec.encode(listOf(keys, bag))))
        assertEquals(emptyList<SavedDevice>(), SavedDeviceCodec.decode(""))
    }

    @Test
    fun `tabs and line breaks in a name cannot break the format`() {
        val odd = keys.copy(name = "My\tkeys\nspare ")
        val read = SavedDeviceCodec.decode(SavedDeviceCodec.encode(listOf(odd)))
        assertEquals(listOf(keys.copy(name = "My keys spare")), read)
    }

    @Test
    fun `unreadable lines are skipped`() {
        val text = "9\tfuture\n" + SavedDeviceCodec.encode(listOf(bag)) + "\ngarbage"
        assertEquals(listOf(bag), SavedDeviceCodec.decode(text))
    }

    @Test
    fun `a type this version does not know becomes the generic one`() {
        val text = SavedDeviceCodec.encode(listOf(keys)).replace("TRACKER", "HOVERBOARD")
        assertEquals(DeviceCategory.UNKNOWN, SavedDeviceCodec.decode(text).single().category)
    }

    @Test
    fun `shows in My devices like a paired device`() {
        assertEquals(PairedDevice(keys.address, "My keys", DeviceCategory.TRACKER), keys.asPaired())
    }
}
