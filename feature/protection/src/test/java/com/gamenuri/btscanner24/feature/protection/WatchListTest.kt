package com.gamenuri.btscanner24.feature.protection

import com.gamenuri.btscanner24.core.ble.WatchTarget
import com.gamenuri.btscanner24.core.model.DeviceCategory
import com.gamenuri.btscanner24.core.model.PairedDevice
import com.gamenuri.btscanner24.core.model.ProtectionSettings
import com.gamenuri.btscanner24.core.model.SavedDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchListTest {
    private val buds = PairedDevice("AA:00:00:00:00:01", "Min's buds", DeviceCategory.EARBUDS, "Galaxy Buds FE")
    private val car = PairedDevice("AA:00:00:00:00:02", "My Car", DeviceCategory.UNKNOWN)
    private val keys = SavedDevice("BB:00:00:00:00:01", "My keys", DeviceCategory.TRACKER, "Tagly 0006", 5L)
    private val budsSaved = SavedDevice(buds.address, "Buds", DeviceCategory.EARBUDS, "Galaxy Buds FE", 6L)

    private val devices = WatchList.build(
        settings = ProtectionSettings(watched = setOf("CC:00:00:00:00:09")),
        saved = listOf(keys, budsSaved),
        paired = listOf(buds, car),
    )

    @Test
    fun `saved devices still on My devices are watched from the start, a paired and saved one once`() {
        assertEquals(listOf(buds.address, keys.address), devices.map { it.address })
        assertEquals(listOf("Min's buds", "My keys"), devices.map { it.name })
    }

    @Test
    fun `a paired device is watched once switched on and a saved one not once switched off`() {
        val settings = ProtectionSettings(watched = setOf(car.address), unwatched = setOf(keys.address))
        val chosen = WatchList.build(settings, saved = listOf(keys, budsSaved), paired = listOf(buds, car))
        assertEquals(listOf(buds.address, car.address), chosen.map { it.address })
    }

    @Test
    fun `the scan listens for each address and each advertised name`() {
        assertEquals(
            listOf(
                WatchTarget(buds.address, null),
                WatchTarget(null, "Min's buds"),
                WatchTarget(null, "Galaxy Buds FE"),
                WatchTarget(keys.address, null),
                WatchTarget(null, "Tagly 0006"),
            ),
            WatchList.targets(devices),
        )
    }

    @Test
    fun `a result belongs to the device with its address, else to the one advertising its name`() {
        assertEquals(keys.address, WatchList.match(devices, keys.address, null)?.address)
        assertEquals(buds.address, WatchList.match(devices, "7F:00:00:00:00:10", " galaxy buds fe")?.address)
        assertNull(WatchList.match(devices, "7F:00:00:00:00:11", "Someone's speaker"))
        assertNull(WatchList.match(devices, "7F:00:00:00:00:12", null))
        assertNull(WatchList.match(devices, "7F:00:00:00:00:13", " "))
    }
}
