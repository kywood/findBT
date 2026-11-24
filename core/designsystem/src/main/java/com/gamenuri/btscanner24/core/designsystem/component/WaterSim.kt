package com.gamenuri.btscanner24.core.designsystem.component

import java.util.Random
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Water surface as a row of spring columns (design motion v3, #107; reference `anim3.py`). Each
 * column has a height offset and a speed: a spring pulls it back to the rest surface, neighbours
 * pull on each other (which carries waves across and reflects them off the walls), and the water
 * level itself is a damped spring towards a target, so it overshoots a little like poured water.
 *
 * Plain numbers, no Android: lengths are design pixels of a [width] × [height] screen with y down,
 * so [level] = [height] is the bottom edge and 0 the top. Call [step] once per [DT].
 */
class WaterSim(val width: Float, val height: Float, seed: Long = 7L) {
    /** Offset of each column's top from [level]; negative is up. */
    val offsets = FloatArray(COLUMNS)
    private val speeds = FloatArray(COLUMNS)
    private val pull = FloatArray(COLUMNS)
    private val random = Random(seed)

    var level = height + START_BELOW
        private set
    var levelSpeed = 0f
        private set

    /** Puts the surface at [level], flat and at rest. */
    fun reset(level: Float) {
        this.level = level
        levelSpeed = 0f
        offsets.fill(0f)
        speeds.fill(0f)
    }

    /** A push at [x] (design px from the left), spread over a few columns; negative is up. */
    fun kick(x: Float, impulse: Float) {
        val centre = (x / width * (COLUMNS - 1)).roundToInt().coerceIn(0, COLUMNS - 1)
        for (i in maxOf(0, centre - KICK_COLUMNS)..minOf(COLUMNS - 1, centre + KICK_COLUMNS)) {
            val d = (i - centre) / (KICK_COLUMNS * 0.6f)
            speeds[i] += impulse * exp(-d * d)
        }
    }

    /** One [DT] towards [targetLevel]; [slope] is tan(roll), the tilt of the rest surface. */
    fun step(targetLevel: Float, slope: Float = 0f) {
        levelSpeed += (LEVEL_STIFFNESS * (targetLevel - level) - LEVEL_DAMPING * levelSpeed) * DT
        level += levelSpeed * DT
        val churn = abs(levelSpeed) * TURBULENCE
        val last = COLUMNS - 1
        for (i in 0..last) {
            val neighbours = when (i) {
                0 -> offsets[1] - offsets[0]
                last -> offsets[last - 1] - offsets[last]
                else -> offsets[i - 1] + offsets[i + 1] - 2f * offsets[i]
            }
            val rest = (i * width / last - width / 2f) * slope
            pull[i] = -STIFFNESS * (offsets[i] - rest) - DAMPING * speeds[i] + TENSION * neighbours
        }
        for (i in 0..last) {
            speeds[i] += pull[i] * DT + random.nextGaussian().toFloat() * churn
            offsets[i] += speeds[i] * DT
        }
    }

    /** Screen y of every column top, lightly smoothed, written into [out] ([COLUMNS] values). */
    fun surface(out: FloatArray) {
        offsets.copyInto(out)
        repeat(SMOOTH_PASSES) {
            var before = out[0]
            for (i in 1 until COLUMNS - 1) {
                val here = out[i]
                out[i] = 0.25f * before + 0.5f * here + 0.25f * out[i + 1]
                before = here
            }
        }
        for (i in 0 until COLUMNS) out[i] += level
    }

    companion object {
        const val COLUMNS = 48

        /** 4 steps per 60 Hz frame. */
        const val DT = 1f / 240f

        /** Where an empty screen's level sits: just under the bottom edge. */
        const val START_BELOW = 30f

        const val STIFFNESS = 180f
        const val DAMPING = 5f
        const val TENSION = 9000f
        const val LEVEL_STIFFNESS = 42f
        const val LEVEL_DAMPING = 8f
        const val TURBULENCE = 0.018f
        const val KICK_COLUMNS = 3
        private const val SMOOTH_PASSES = 2
    }
}
