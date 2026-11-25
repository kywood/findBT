package com.gamenuri.btscanner24.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsEventTest {
    private val all = listOf(
        AnalyticsEvent.Permission(true),
        AnalyticsEvent.Permission(false),
        AnalyticsEvent.FindStarted(FindSource.NEARBY),
        AnalyticsEvent.FindStarted(FindSource.MY_DEVICES),
        AnalyticsEvent.DeviceSaved,
        AnalyticsEvent.SaveLockedShown,
        AnalyticsEvent.PaywallShown,
        AnalyticsEvent.DayPassStarted,
    ) + FindTime.entries.map { AnalyticsEvent.FindFound(it) }

    /** Firebase's limits, and our rule: only fixed lowercase words (#19). */
    private val word = Regex("[a-z][a-z0-9_]{0,39}")

    @Test
    fun `names, keys and values are fixed words only`() {
        for (event in all) {
            assertTrue(event.name, word.matches(event.name))
            for ((key, value) in event.params) {
                assertTrue("${event.name}.$key", word.matches(key))
                assertTrue("${event.name}.$key=$value", word.matches(value))
            }
        }
    }

    @Test
    fun `nothing that looks like scan data is ever a parameter`() {
        val banned = listOf("name", "address", "mac", "rssi", "uuid", "company", "maker", "location", "lat", "lon")
        for (event in all) {
            for (key in event.params.keys) assertTrue("${event.name}.$key", banned.none { it == key })
        }
    }

    @Test
    fun `find time is reported in wide bands`() {
        assertEquals(FindTime.UNDER_1_MIN, FindTime.of(0))
        assertEquals(FindTime.UNDER_1_MIN, FindTime.of(59))
        assertEquals(FindTime.UNDER_3_MIN, FindTime.of(60))
        assertEquals(FindTime.UNDER_10_MIN, FindTime.of(180))
        assertEquals(FindTime.LONGER, FindTime.of(600))
        assertEquals("under_1_min", AnalyticsEvent.FindFound(FindTime.of(12)).params["time"])
    }
}
