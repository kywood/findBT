package com.gamenuri.btscanner24.core.ui.map3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapHitsTest {
    private val hits = MapHits(
        keys = listOf("far", "near", "hidden"),
        spots = floatArrayOf(
            100f, 100f, 40f,
            130f, 100f, 80f,
            500f, 500f, 0f,
        ),
    )

    @Test
    fun `a tap on a device finds it`() {
        assertEquals("far", hits.keyAt(80f, 100f))
    }

    @Test
    fun `a tap on the empty floor finds nothing`() {
        assertNull(hits.keyAt(300f, 300f))
        assertNull(MapHits.NONE.keyAt(0f, 0f))
    }

    @Test
    fun `where two overlap the one whose middle is closer for its size wins`() {
        // 5 px from "far" (an eighth of its size) and 25 px from "near" (under a third of its size).
        assertEquals("far", hits.keyAt(105f, 100f))
        // 20 px from "far" (half its size) and 10 px from "near" (an eighth).
        assertEquals("near", hits.keyAt(120f, 100f))
    }

    @Test
    fun `a device that is out of sight is never hit`() {
        assertNull(hits.keyAt(500f, 500f))
    }
}
