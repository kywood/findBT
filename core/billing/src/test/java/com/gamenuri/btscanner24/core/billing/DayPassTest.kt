package com.gamenuri.btscanner24.core.billing

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayPassTest {
    private class MemoryStorage : DayPass.Storage {
        override var until: Long = 0L
        override var grantedOnEpochDay: Long = Long.MIN_VALUE
    }

    private val zone = ZoneId.of("Asia/Seoul")
    private val pass = DayPass(MemoryStorage()) { zone }

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `a pass lasts 24 hours`() {
        val start = at(24, 15, 20)
        assertEquals(start + DayPass.DURATION_MS, pass.grant(start))
        assertEquals(start + DayPass.DURATION_MS, pass.until(at(25, 15, 19)))
        assertNull(pass.until(at(25, 15, 21)))
    }

    @Test
    fun `one pass a day`() {
        assertTrue(pass.isAvailable(at(24, 9)))
        pass.grant(at(24, 9))
        assertFalse(pass.isAvailable(at(24, 20)))
        assertNull(pass.grant(at(24, 20)))
        // Next day, while the first pass is still running: not yet.
        assertFalse(pass.isAvailable(at(25, 8)))
        // It ended at 9:00; a new one may start the same day.
        assertTrue(pass.isAvailable(at(25, 9, 1)))
    }
}
