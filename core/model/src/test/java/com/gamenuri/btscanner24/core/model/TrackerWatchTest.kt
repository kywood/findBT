package com.gamenuri.btscanner24.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerWatchTest {
    private val tag = "7A:00:00:00:00:01"
    private val minute = 60_000L

    /** The phone keeps moving and the tracker is heard every minute from [from] to [to] (in minutes). */
    private fun TrackerWatch.travel(from: Int, to: Int, address: String = tag, rssi: Int = -60) {
        for (m in from..to) {
            onMoved(m * minute)
            onHeard(address, m * minute + 1_000, rssi)
        }
    }

    @Test
    fun `a tracker heard on the move for over half an hour is reported once`() {
        val watch = TrackerWatch()
        watch.travel(0, 35)
        val due = watch.due(emptySet(), 36 * minute)
        assertEquals(listOf(TrackerAlert(tag, 1_000, 35 * minute + 1_000)), due)
        watch.travel(36, 50)
        assertTrue(watch.due(emptySet(), 51 * minute).isEmpty())
    }

    @Test
    fun `not before half an hour and three stretches of movement`() {
        val watch = TrackerWatch()
        watch.travel(0, 25)
        assertTrue(watch.due(emptySet(), 26 * minute).isEmpty())
    }

    @Test
    fun `a tracker heard only while the phone lies still is never reported`() {
        val watch = TrackerWatch()
        watch.onMoved(0)
        for (m in 5..120) watch.onHeard(tag, m * minute, -50)
        assertTrue(watch.due(emptySet(), 121 * minute).isEmpty())
    }

    @Test
    fun `weak signals do not count`() {
        val watch = TrackerWatch()
        watch.travel(0, 40, rssi = -92)
        assertTrue(watch.due(emptySet(), 41 * minute).isEmpty())
    }

    @Test
    fun `a tracker the user knows is not reported`() {
        val watch = TrackerWatch()
        watch.travel(0, 40)
        assertTrue(watch.due(setOf(tag), 41 * minute).isEmpty())
    }

    @Test
    fun `a tracker that is gone again is not reported`() {
        val watch = TrackerWatch()
        watch.travel(0, 40)
        assertTrue(watch.due(emptySet(), 50 * minute).isEmpty())
    }

    @Test
    fun `passing the same tracker at the door twice a day does not add up`() {
        val watch = TrackerWatch()
        watch.travel(0, 2)
        watch.travel(480, 482)
        watch.travel(1_440, 1_442)
        assertTrue(watch.due(emptySet(), 1_443 * minute).isEmpty())
    }

    @Test
    fun `after a long silence a tracker starts from nothing and can be reported again`() {
        val watch = TrackerWatch()
        watch.travel(0, 35)
        assertEquals(1, watch.due(emptySet(), 36 * minute).size)
        watch.travel(200, 220)
        assertTrue(watch.due(emptySet(), 221 * minute).isEmpty())
        watch.travel(221, 236)
        assertEquals(listOf(200 * minute + 1_000), watch.due(emptySet(), 237 * minute).map { it.firstHeardAt })
    }

    @Test
    fun `forgetting a tracker drops what was counted`() {
        val watch = TrackerWatch()
        watch.travel(0, 35)
        watch.forget(tag)
        assertTrue(watch.due(emptySet(), 36 * minute).isEmpty())
        assertEquals(null, watch.lastHeardAt(tag))
    }

    @Test
    fun `alerts survive storage and broken lines are skipped`() {
        val alerts = listOf(TrackerAlert(tag, 10, 20), TrackerAlert("7A:00:00:00:00:02", 30, 40))
        assertEquals(alerts, TrackerAlertCodec.decode(TrackerAlertCodec.encode(alerts)))
        assertEquals(alerts.take(1), TrackerAlertCodec.decode("$tag\t10\t20\nbroken\n\t1\t2\nx\ty\t3"))
        assertTrue(TrackerAlertCodec.decode("").isEmpty())
    }
}
