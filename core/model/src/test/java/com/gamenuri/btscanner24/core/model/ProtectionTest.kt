package com.gamenuri.btscanner24.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionTest {
    private val keys = "AA:00:00:00:00:01"
    private val buds = "AA:00:00:00:00:02"
    private val start = 1_000_000L
    private val grace = LeftBehindWatch.GRACE_MS

    private fun LeftBehindWatch.dueAt(
        now: Long,
        connected: Set<String> = emptySet(),
        muted: Map<String, Long> = emptyMap(),
    ) = due(setOf(keys, buds), connected, muted, now).map { it.address }

    @Test
    fun `walking away from a device that was heard raises one alert after the grace time`() {
        val watch = LeftBehindWatch(motionKnown = true)
        watch.onHeard(keys, start)
        watch.onMoved(start + 30_000)
        assertTrue(watch.dueAt(start + grace - 1).isEmpty())
        val alerts = watch.due(setOf(keys), emptySet(), emptyMap(), start + grace)
        assertEquals(listOf(LeftBehindWatch.Alert(keys, start)), alerts)
        assertTrue(watch.dueAt(start + grace + 60_000).isEmpty())
    }

    @Test
    fun `a device never heard since watching began raises nothing`() {
        val watch = LeftBehindWatch(motionKnown = false)
        assertTrue(watch.dueAt(start + 10 * grace).isEmpty())
    }

    @Test
    fun `a phone that did not move has left nothing behind`() {
        val watch = LeftBehindWatch(motionKnown = true)
        watch.onMoved(start - 10 * 60_000)
        watch.onHeard(buds, start)
        assertTrue(watch.dueAt(start + 2 * grace).isEmpty())
        watch.onMoved(start + 2 * grace)
        assertEquals(listOf(buds), watch.dueAt(start + 2 * grace + 1))
    }

    @Test
    fun `without a motion sensor the grace time alone decides`() {
        val watch = LeftBehindWatch(motionKnown = false)
        watch.onHeard(keys, start)
        assertEquals(listOf(keys), watch.dueAt(start + grace))
    }

    @Test
    fun `a signal that comes back within the grace time is not an alert`() {
        val watch = LeftBehindWatch(motionKnown = false)
        watch.onHeard(keys, start)
        assertFalse(watch.onHeard(keys, start + grace - 1_000))
        assertTrue(watch.dueAt(start + grace).isEmpty())
    }

    @Test
    fun `Bluetooth going off is not a device left behind`() {
        val watch = LeftBehindWatch(motionKnown = false)
        watch.onHeard(keys, start)
        watch.onBlind(start + 60_000)
        assertTrue(watch.dueAt(start + 2 * grace).isEmpty())
        // Heard again once the watch can hear, then really gone.
        watch.onHeard(keys, start + 3 * grace)
        assertEquals(listOf(keys), watch.dueAt(start + 4 * grace))
    }

    @Test
    fun `connected and muted devices stay quiet`() {
        val watch = LeftBehindWatch(motionKnown = false)
        watch.onHeard(keys, start)
        watch.onHeard(buds, start)
        val now = start + grace
        assertEquals(listOf(keys), watch.dueAt(now, connected = setOf(buds)))
        val again = LeftBehindWatch(motionKnown = false)
        again.onHeard(keys, start)
        assertTrue(again.dueAt(now, muted = mapOf(keys to now + 1)).isEmpty())
        assertEquals(listOf(keys), again.dueAt(now + 1, muted = mapOf(keys to now + 1)))
    }

    @Test
    fun `hearing it again ends the alert and a new one can follow`() {
        val watch = LeftBehindWatch(motionKnown = false)
        watch.onHeard(keys, start)
        assertEquals(listOf(keys), watch.dueAt(start + grace))
        assertTrue(watch.onHeard(keys, start + 2 * grace))
        assertEquals(listOf(keys), watch.dueAt(start + 3 * grace))
    }

    @Test
    fun `a device taken off the watch is forgotten`() {
        val watch = LeftBehindWatch(motionKnown = false)
        watch.onHeard(keys, start)
        watch.forget(keys)
        assertTrue(watch.dueAt(start + grace).isEmpty())
        assertEquals(null, watch.lastHeardAt(keys))
    }

    @Test
    fun `times round-trip as text and unreadable lines are skipped`() {
        val times = mapOf(keys to 5L, buds to 7L)
        assertEquals(times, TimesCodec.decode(TimesCodec.encode(times)))
        assertEquals(mapOf(keys to 5L), TimesCodec.decode("garbage\n$keys\t5\n$buds\tsoon\n\t9"))
        assertEquals(emptyMap<String, Long>(), TimesCodec.decode(""))
    }
}
