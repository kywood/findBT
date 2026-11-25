package com.gamenuri.btscanner24.feature.devices

import com.gamenuri.btscanner24.core.model.AdvertSignature
import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.DeviceCategory
import com.gamenuri.btscanner24.core.model.LastSeen
import com.gamenuri.btscanner24.core.model.LastSeenPolicy
import com.gamenuri.btscanner24.core.model.PairedDevice
import com.gamenuri.btscanner24.core.model.SavedDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LastSeenRecorderTest {

    private val now = 10_000_000L
    private val buds = PairedDevice("AA:00:00:00:00:01", "Galaxy Buds2 Pro", DeviceCategory.EARBUDS)

    private fun seen(address: String, name: String?, lastSeen: Long = now) =
        BleDevice(address = address, name = name, rssi = -60, firstSeen = 0, lastSeen = lastSeen)

    private fun due(
        seen: List<BleDevice>,
        known: Map<String, LastSeen> = emptyMap(),
        saved: List<SavedDevice> = emptyList(),
    ) = LastSeenRecorder.due(listOf(buds), saved, seen, known, now)

    private val tag = SavedDevice(
        address = "BB:00:00:00:00:01",
        name = "My keys",
        category = DeviceCategory.TRACKER,
        advertisedName = "Tagly 0006",
        savedAt = 5L,
        signature = AdvertSignature(companyId = 0x0157, serviceUuids16 = setOf(0xFE95), DeviceCategory.TRACKER),
    )

    private fun tagHeard(address: String) = seen(address, "Tagly 0006").copy(
        companyId = 0x0157,
        serviceUuids16 = listOf(0xFE95),
        category = DeviceCategory.TRACKER,
    )

    @Test
    fun `a saved device under a new address is written when its advertisement still agrees`() {
        assertEquals(listOf(tag.address to now), due(listOf(tagHeard("7F:00:00:00:00:10")), saved = listOf(tag)))
    }

    @Test
    fun `the same name alone never writes a saved device`() {
        val sameModel = tagHeard("7F:00:00:00:00:11").copy(companyId = 0x004C)
        val nameOnly = seen("7F:00:00:00:00:12", "Tagly 0006")
        assertTrue(due(listOf(sameModel), saved = listOf(tag)).isEmpty())
        assertTrue(due(listOf(nameOnly), saved = listOf(tag)).isEmpty())
    }

    @Test
    fun `a saved device with nothing kept counts under its own address only`() {
        val bare = tag.copy(signature = null)
        assertTrue(due(listOf(tagHeard("7F:00:00:00:00:13")), saved = listOf(bare)).isEmpty())
        assertEquals(listOf(tag.address to now), due(listOf(tagHeard(tag.address)), saved = listOf(bare)))
    }

    @Test
    fun `a paired device being heard is written under its paired address`() {
        val heard = seen("7F:11:22:33:44:55", "Galaxy Buds2 Pro", lastSeen = now - 2_000)
        assertEquals(listOf(buds.address to now - 2_000), due(listOf(heard)))
    }

    @Test
    fun `other people's devices are never written`() {
        assertTrue(due(listOf(seen("7F:11:22:33:44:77", "Someone's speaker"))).isEmpty())
    }

    @Test
    fun `a sighting that has gone quiet is not written again`() {
        val old = seen(buds.address, null, lastSeen = now - MyDevices.ACTIVE_WINDOW_MS - 1)
        assertTrue(due(listOf(old)).isEmpty())
    }

    @Test
    fun `a fresh record waits for the minimum gap`() {
        val heard = listOf(seen(buds.address, null))
        val fresh = mapOf(buds.address to LastSeen(buds.address, now - LastSeenPolicy.MIN_GAP_MS + 1, null))
        val stale = mapOf(buds.address to LastSeen(buds.address, now - LastSeenPolicy.MIN_GAP_MS, null))
        assertTrue(due(heard, fresh).isEmpty())
        assertEquals(listOf(buds.address to now), due(heard, stale))
    }
}
