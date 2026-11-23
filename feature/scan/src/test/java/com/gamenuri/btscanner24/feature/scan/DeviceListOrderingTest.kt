package com.gamenuri.btscanner24.feature.scan

import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.CategoryGroup
import com.gamenuri.btscanner24.core.model.DeviceCategory
import com.gamenuri.btscanner24.core.model.DeviceFilter
import com.gamenuri.btscanner24.core.model.DeviceSort
import com.gamenuri.btscanner24.core.model.DistanceBucket
import com.gamenuri.btscanner24.core.model.ListPreferences
import com.gamenuri.btscanner24.core.model.SignalLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceListOrderingTest {

    private val now = 100_000L
    private val all = ListPreferences()
    private val noFilter = DeviceFilter()

    private fun device(
        address: String,
        name: String? = null,
        rssi: Int = -70,
        firstSeen: Long = 0,
        lastSeen: Long = now,
        category: DeviceCategory = DeviceCategory.EARBUDS,
        manufacturer: String? = null,
        distance: Double? = null,
    ) = BleDevice(
        address = address,
        name = name,
        rssi = rssi,
        firstSeen = firstSeen,
        lastSeen = lastSeen,
        category = category,
        manufacturer = manufacturer,
        distanceMeters = distance,
    )

    private fun addresses(filter: DeviceFilter, vararg devices: BleDevice, query: String = "") =
        DeviceListOrdering.filter(devices.toList(), filter, query, now).all.map { it.address }

    @Test
    fun `unnamed devices are shown by default and hidden by the switch`() {
        val devices = arrayOf(device("A", "Buds"), device("B"))
        assertEquals(listOf("A", "B"), addresses(noFilter, *devices))
        assertEquals(listOf("A"), addresses(noFilter.copy(showUnnamed = false), *devices))
    }

    @Test
    fun `query matches name or manufacturer`() {
        val devices = arrayOf(
            device("1", "Galaxy Buds", manufacturer = "Samsung"),
            device("2", "Watch", manufacturer = "Apple"),
        )
        assertEquals(listOf("1"), addresses(noFilter, *devices, query = "buds"))
        assertEquals(listOf("2"), addresses(noFilter, *devices, query = "apple"))
    }

    @Test
    fun `category chips filter by group`() {
        val devices = arrayOf(
            device("buds", category = DeviceCategory.EARBUDS),
            device("watch", category = DeviceCategory.FITNESS_BAND),
            device("tag", category = DeviceCategory.TRACKER),
        )
        val filter = noFilter.copy(types = setOf(CategoryGroup.WATCH, CategoryGroup.TRACKER))
        assertEquals(listOf("watch", "tag"), addresses(filter, *devices))
    }

    @Test
    fun `gone devices are hidden only when the filter is on`() {
        val devices = arrayOf(device("fresh"), device("old", lastSeen = now - 31_000))
        assertEquals(2, addresses(noFilter, *devices).size)
        assertEquals(listOf("fresh"), addresses(noFilter.copy(hideGone = true), *devices))
    }

    @Test
    fun `other devices are hidden by default and only counted`() {
        val devices = listOf(
            device("buds", "Buds"),
            device("tv", "Living Room TV", category = DeviceCategory.TV),
            device("beacon", category = DeviceCategory.UNKNOWN),
            device("keyboard", "K380", category = DeviceCategory.KEYBOARD),
        )
        val hidden = DeviceListOrdering.filter(devices, noFilter, "", now)
        assertEquals(listOf("buds"), hidden.personal.map { it.address })
        assertTrue(hidden.other.isEmpty())
        assertEquals(3, hidden.hiddenOther)

        val shown = DeviceListOrdering.filter(devices, noFilter.copy(showOther = true), "", now)
        assertEquals(listOf("buds"), shown.personal.map { it.address })
        assertEquals(listOf("tv", "beacon", "keyboard"), shown.other.map { it.address })
        assertEquals(0, shown.hiddenOther)
    }

    @Test
    fun `paired devices are personal whatever their type`() {
        val tv = device("tv", "TV", category = DeviceCategory.TV)
        val result = DeviceListOrdering.filter(listOf(tv), noFilter, "", now, paired = setOf("tv"))
        assertEquals(listOf("tv"), result.personal.map { it.address })
    }

    @Test
    fun `unnamed phones and laptops are other, named ones personal`() {
        val devices = listOf(
            device("mine", "Galaxy S23", category = DeviceCategory.PHONE),
            device("someone", category = DeviceCategory.PHONE),
            device("pc", category = DeviceCategory.COMPUTER),
        )
        val result = DeviceListOrdering.filter(devices, noFilter, "", now)
        assertEquals(listOf("mine"), result.personal.map { it.address })
        assertEquals(2, result.hiddenOther)
    }

    @Test
    fun `searching or the Other chip shows other devices even with the switch off`() {
        val tv = device("tv", "Living Room TV", category = DeviceCategory.TV)
        assertEquals(listOf("tv"), addresses(noFilter, tv, query = "living"))
        assertEquals(listOf("tv"), addresses(noFilter.copy(types = setOf(CategoryGroup.OTHER)), tv))
    }

    @Test
    fun `distance and signal chips`() {
        val near = device("near", rssi = -50, distance = 2.0)
        val mid = device("mid", rssi = -70, distance = 7.0)
        val far = device("far", rssi = -90, distance = 15.0)
        val unknown = device("unknown", rssi = -90)
        val buckets = noFilter.copy(distances = setOf(DistanceBucket.WITHIN_5_M, DistanceBucket.OVER_10_M))
        assertEquals(listOf("near", "far"), addresses(buckets, near, mid, far, unknown))
        assertEquals(listOf("mid"), addresses(noFilter.copy(signals = setOf(SignalLevel.MEDIUM)), near, mid, far))
    }

    @Test
    fun `manufacturer chips`() {
        val devices = arrayOf(device("s", manufacturer = "Samsung"), device("a", manufacturer = "Apple"), device("n"))
        assertEquals(listOf("s"), addresses(noFilter.copy(manufacturers = setOf("Samsung")), *devices))
    }

    @Test
    fun `badge counts switches off their default and chip groups in use`() {
        assertEquals(0, noFilter.activeCount)
        val filter = noFilter.copy(
            showOther = true,
            showUnnamed = false,
            types = setOf(CategoryGroup.EARBUDS, CategoryGroup.WATCH),
            distances = setOf(DistanceBucket.WITHIN_5_M),
        )
        assertEquals(4, filter.activeCount)
    }

    @Test
    fun `other devices stay below personal ones in the list`() {
        val ordering = OrderingState()
        val prefs = all.copy(filter = noFilter.copy(showOther = true))
        val devices = listOf(
            device("tv", "TV", rssi = -30, category = DeviceCategory.TV),
            device("buds", "Buds", rssi = -80),
        )
        val list = ordering.update(devices, prefs, "", 0, now)
        assertEquals(listOf("buds", "tv"), list.items.map { it.device.address })
        assertEquals(listOf(false, true), list.items.map { it.isOther })
    }

    @Test
    fun `nearest sort uses smoothed rssi`() {
        val devices = listOf(device("A", rssi = -80), device("B", rssi = -40))
        val sorted = DeviceListOrdering.sort(devices, DeviceSort.NEAREST)
        assertEquals(listOf("B", "A"), sorted.map { it.address })
    }

    @Test
    fun `name sort puts unnamed last`() {
        val devices = listOf(device("x"), device("b", "beta"), device("a", "Alpha"))
        val sorted = DeviceListOrdering.sort(devices, DeviceSort.NAME)
        assertEquals(listOf("a", "b", "x"), sorted.map { it.address })
    }

    @Test
    fun `rows keep their place while scanning and new ones go to the bottom`() {
        val ordering = OrderingState()
        ordering.update(listOf(device("A", rssi = -50), device("B", rssi = -60)), all, "", 0, now)
        // After the settle window, B gets closer than A and C appears: nothing moves, C is appended as new.
        val later = now + OrderingState.SETTLE_MS + 1
        val (items, reorder, _) = ordering.update(
            listOf(device("A", rssi = -80), device("B", rssi = -40), device("C", rssi = -30, firstSeen = later)),
            all,
            "",
            0,
            later,
        )
        assertEquals(listOf("A", "B", "C"), items.map { it.device.address })
        assertTrue(items.last().isNew)
        assertTrue(reorder)
    }

    @Test
    fun `devices arriving during the settle window are sorted, not marked new`() {
        val ordering = OrderingState()
        ordering.update(listOf(device("A", rssi = -60)), all, "", 0, now)
        val both = listOf(device("A", rssi = -60), device("B", rssi = -40))
        val (items, _, _) = ordering.update(both, all, "", 0, now + 1_000)
        assertEquals(listOf("B", "A"), items.map { it.device.address })
        assertFalse(items.any { it.isNew })
    }

    @Test
    fun `refresh order applies the sort and clears new badges`() {
        val ordering = OrderingState()
        ordering.update(listOf(device("A", rssi = -50)), all, "", 0, now)
        val later = now + OrderingState.SETTLE_MS + 1
        val both = listOf(device("A", rssi = -50), device("C", rssi = -30))
        ordering.update(both, all, "", 0, later)
        val (items, reorder, _) = ordering.update(both, all, "", 1, later + 1)
        assertEquals(listOf("C", "A"), items.map { it.device.address })
        assertFalse(items.any { it.isNew })
        assertFalse(reorder)
    }
}
