package com.gamenuri.btscanner24.core.designsystem.component

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterSimTest {
    private fun WaterSim.run(seconds: Float, target: Float, slope: Float = 0f) {
        repeat((seconds / WaterSim.DT).toInt()) { step(target, slope) }
    }

    @Test
    fun `starts just under the bottom edge`() {
        val sim = WaterSim(540f, 1170f)
        assertEquals(1170f + WaterSim.START_BELOW, sim.level, 0f)
    }

    @Test
    fun `level settles on the target after a small overshoot`() {
        val sim = WaterSim(540f, 1170f)
        var highest = Float.MAX_VALUE
        repeat((3f / WaterSim.DT).toInt()) {
            sim.step(14f)
            highest = minOf(highest, sim.level)
        }
        assertEquals(14f, sim.level, 1f)
        // About 8 % past the target on the way (damping ratio 0.62), never wildly more.
        val overshoot = (14f - highest) / (1170f + WaterSim.START_BELOW - 14f)
        assertTrue("overshoot $overshoot", overshoot in 0.04f..0.12f)
    }

    @Test
    fun `a kick makes ripples that stay bounded and die out`() {
        val sim = WaterSim(540f, 1170f)
        sim.reset(500f)
        sim.kick(47f, -1500f)
        var largest = 0f
        repeat((0.5f / WaterSim.DT).toInt()) {
            sim.step(500f)
            largest = maxOf(largest, sim.offsets.maxOf { abs(it) })
        }
        assertTrue("ripple $largest", largest in 5f..200f)
        // The far wall has felt it: waves cross the width in about half a second.
        sim.run(4f, 500f)
        assertTrue(sim.offsets.all { abs(it) < 1f })
    }

    @Test
    fun `a tilted phone tilts the resting surface`() {
        val sim = WaterSim(540f, 1170f)
        sim.reset(500f)
        sim.run(4f, 500f, slope = 0.2f)
        val tops = FloatArray(WaterSim.COLUMNS).also(sim::surface)
        // Right edge higher (positive roll): the water sits lower on the right, so y is larger there.
        assertTrue(tops.last() - tops.first() > 50f)
        assertEquals(500f, tops[WaterSim.COLUMNS / 2], 6f)
    }

    @Test
    fun `surface is the level plus the smoothed offsets`() {
        val sim = WaterSim(540f, 1170f)
        sim.reset(300f)
        val tops = FloatArray(WaterSim.COLUMNS).also(sim::surface)
        assertTrue(tops.all { it == 300f })
    }
}
