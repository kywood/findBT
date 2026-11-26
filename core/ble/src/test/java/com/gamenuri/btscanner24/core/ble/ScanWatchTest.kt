package com.gamenuri.btscanner24.core.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanWatchTest {
    private val watch = ScanWatch(silenceMs = 12_000, maxRunMs = 1_200_000)

    @Test
    fun `a scan that keeps delivering is left alone`() {
        assertFalse(watch.shouldRestart(now = 600_000, startedAt = 0, lastResultAt = 599_000))
    }

    @Test
    fun `a scan that never delivered anything is restarted after the silence limit`() {
        assertFalse(watch.shouldRestart(now = 12_000, startedAt = 0, lastResultAt = 0))
        assertTrue(watch.shouldRestart(now = 12_001, startedAt = 0, lastResultAt = 0))
    }

    @Test
    fun `a scan that went quiet is restarted`() {
        assertTrue(watch.shouldRestart(now = 100_000, startedAt = 0, lastResultAt = 80_000))
    }

    @Test
    fun `a long scan is restarted before the platform downgrades it`() {
        assertTrue(watch.shouldRestart(now = 1_200_001, startedAt = 0, lastResultAt = 1_200_000))
    }
}
