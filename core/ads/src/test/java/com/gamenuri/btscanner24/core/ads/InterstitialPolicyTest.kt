package com.gamenuri.btscanner24.core.ads

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterstitialPolicyTest {
    private class MemoryStorage : InterstitialPolicy.Storage {
        override var epochDay: Long = Long.MIN_VALUE
        override var findsToday: Int = 0
        override var shownToday: Int = 0
        override var lastShownAt: Long = 0L
    }

    private val zone = ZoneId.of("Asia/Seoul")

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun policy(installedDay: Int = 20) = InterstitialPolicy(MemoryStorage(), at(installedDay, 9)) { zone }

    /** Runs a find at each time; shows the ad whenever allowed. Returns which finds got one. */
    private fun InterstitialPolicy.run(vararg times: Long): List<Boolean> = times.map { now ->
        onFindDone(now).also { if (it) onShown(now) }
    }

    @Test
    fun `never on the install day`() {
        assertEquals(listOf(false, false, false), policy(installedDay = 24).run(at(24, 10), at(24, 11), at(24, 12)))
    }

    @Test
    fun `not after the first find of the day, then at most two`() {
        val shown = policy().run(at(24, 10), at(24, 11), at(24, 12), at(24, 13), at(24, 14))
        assertEquals(listOf(false, true, true, false, false), shown)
    }

    @Test
    fun `at least ten minutes apart`() {
        val shown = policy().run(at(24, 10), at(24, 11), at(24, 11, 5), at(24, 11, 11))
        assertEquals(listOf(false, true, false, true), shown)
    }

    @Test
    fun `the count starts again the next day`() {
        val policy = policy()
        policy.run(at(24, 10), at(24, 11), at(24, 12))
        assertFalse(policy.onFindDone(at(25, 10)))
        assertTrue(policy.onFindDone(at(25, 11)))
    }
}
