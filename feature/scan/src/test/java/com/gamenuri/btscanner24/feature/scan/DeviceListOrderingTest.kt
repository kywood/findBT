package com.gamenuri.btscanner24.feature.scan

import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.CategoryGroup
import com.gamenuri.btscanner24.core.model.DeviceCategory
import com.gamenuri.btscanner24.core.model.DeviceSort
import com.gamenuri.btscanner24.core.model.ListPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceListOrderingTest {

    private val now = 100_000L
    private val all = ListPreferences(namedOnly = false)

    private fun device(
        address: String,
        name: String? = null,
        rssi: Int = -70,
        firstSeen: Long = 0,
        lastSeen: Long = now,
        category: DeviceCategory = DeviceCategory.UNKNOWN,
        manufacturer: String? = null,
    ) = BleDevice(
        address = address,
        name = name,
        rssi = rssi,
        firstSeen = firstSeen,
        lastSeen = lastSeen,
        category = category,
        manufacturer = manufacturer,
    )

    @Test
    fun `named only is off by default and drops unnamed devices when on`() {
        val devices = listOf(device("A", "Buds"), device("B"))
        assertEquals(
            listOf("A", "B"),
            DeviceListOrdering.filter(devices, ListPreferences(), "", now).map {
                it.address
            },
        )
        val namedOnly = ListPreferences(namedOnly = true)
        assertEquals(listOf("A"), DeviceListOrdering.filter(devices, namedOnly, "", now).map { it.address })
    }

    @Test
    fun `query matches name or manufacturer`() {
        val devices = listOf(
            device("1", "Galaxy Buds", manufacturer = "Samsung"),
            device("2", "Watch", manufacturer = "Apple"),
        )
        assertEquals(listOf("1"), DeviceListOrdering.filter(devices, all, "buds", now).map { it.address })
        assertEquals(listOf("2"), DeviceListOrdering.filter(devices, all, "apple", now).map { it.address })
    }

    @Test
    fun `category chips filter by group`() {
        val devices = listOf(
            device("buds", category = DeviceCategory.EARBUDS),
            device("watch", category = DeviceCategory.FITNESS_BAND),
            device("tag", category = DeviceCategory.TRACKER),
        )
        val prefs = all.copy(groups = setOf(CategoryGroup.WATCH, CategoryGroup.TRACKER))
        assertEquals(listOf("watch", "tag"), DeviceListOrdering.filter(devices, prefs, "", now).map { it.address })
    }

    @Test
    fun `gone devices are hidden only when the filter is on`() {
        val devices = listOf(device("fresh"), device("old", lastSeen = now - 31_000))
        assertEquals(2, DeviceListOrdering.filter(devices, all, "", now).size)
        val hidden = DeviceListOrdering.filter(devices, all.copy(hideGone = true), "", now)
        assertEquals(listOf("fresh"), hidden.map { it.address })
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
        val (items, reorder) = ordering.update(
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
        val (items, _) = ordering.update(both, all, "", 0, now + 1_000)
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
        val (items, reorder) = ordering.update(both, all, "", 1, later + 1)
        assertEquals(listOf("C", "A"), items.map { it.device.address })
        assertFalse(items.any { it.isNew })
        assertFalse(reorder)
    }
}
