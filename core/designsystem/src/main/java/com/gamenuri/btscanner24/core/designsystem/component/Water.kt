package com.gamenuri.btscanner24.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

/** About how long [WaterMotion.rise] takes to cover the screen; for things timed to follow it. */
const val WATER_RISE_MS = 1_000

/**
 * One body of water that fills or leaves whatever [water] is applied to (#107): a [WaterSim] stepped
 * from the frame clock. Start it only once the content has drawn a frame; from then on a slow frame
 * slows the water for a moment instead of skipping part of it (#106).
 */
@Stable
class WaterMotion(full: Boolean = false) {
    internal var phase by mutableStateOf(if (full) Phase.FULL else Phase.EMPTY)
        private set

    /** Bumped once per simulated frame; the draw pass reads it, so only drawing is redone. */
    internal var frame by mutableIntStateOf(0)
        private set
    internal var sim: WaterSim? = null
        private set
    internal var size = IntSize.Zero
    internal val fade = Animatable(if (full) 1f else 0f)
    private var run = 0

    /** True once the content is fully shown and nothing moves. */
    val isFull: Boolean get() = phase == Phase.FULL

    /** True while the water is rising or draining; pause other continuous animations meanwhile. */
    val isMoving: Boolean get() = phase == Phase.RISING || phase == Phase.DRAINING

    /** Shows the content at once. */
    fun fill() {
        run++
        sim = null
        phase = Phase.FULL
    }

    /**
     * Fills from the bottom edge. [kickAt] is where the tap was, 0..1 across the width; [slope]
     * gives tan(roll) so the surface stays level when the phone leans. With [reduceMotion] the
     * content just fades in.
     */
    suspend fun rise(kickAt: Float = 0.5f, reduceMotion: Boolean = false, slope: () -> Float = { 0f }) {
        val mine = ++run
        if (reduceMotion) {
            crossfade(mine, to = 1f, end = Phase.FULL)
            return
        }
        val water = newSim().also { it.kick(kickAt * it.width, TAP_IMPULSE) }
        sim = water
        fade.snapTo(1f)
        phase = Phase.RISING
        var reachedBrimAt = -1f
        simulate(mine, water, slope) { seconds ->
            if (reachedBrimAt < 0f && water.level < BRIM_NEAR) reachedBrimAt = seconds
            // To the brim first, so the overshoot and ripples show at the top, then over it.
            val overfill = reachedBrimAt >= 0f && seconds - reachedBrimAt >= BRIM_HOLD_SECONDS
            if (overfill && water.level < COVERED_LEVEL) null else if (overfill) OVERFILL_LEVEL else BRIM_LEVEL
        }
        if (mine == run) fill()
    }

    /** Runs out through the bottom edge, from full. With [reduceMotion] the content fades out. */
    suspend fun drain(reduceMotion: Boolean = false, slope: () -> Float = { 0f }) {
        val mine = ++run
        if (reduceMotion) {
            crossfade(mine, to = 0f, end = Phase.EMPTY)
            return
        }
        val water = newSim()
        // Carry on from a rise that was still going; otherwise start just over the top.
        val from = sim?.level?.takeIf { phase == Phase.RISING } ?: OVERFILL_LEVEL
        water.reset(from)
        water.kick(water.width / 2f, DRAIN_IMPULSE)
        sim = water
        fade.snapTo(1f)
        phase = Phase.DRAINING
        val gone = water.height + DRAINED_BELOW
        simulate(mine, water, slope) { if (water.level > gone) null else water.height + DRAIN_TARGET_BELOW }
        if (mine == run) {
            sim = null
            phase = Phase.EMPTY
        }
    }

    private fun newSim(): WaterSim {
        val height = if (size.width > 0) size.height * DESIGN_WIDTH / size.width else DESIGN_HEIGHT
        return WaterSim(DESIGN_WIDTH, height)
    }

    /** Steps [water] every frame towards the level [target] returns, until it returns null. */
    private suspend fun simulate(
        mine: Int,
        water: WaterSim,
        slope: () -> Float,
        target: (seconds: Float) -> Float?,
    ) {
        var last = withFrameNanos { it }
        var seconds = 0f
        var owed = 0f
        var done = false
        while (!done && mine == run && seconds < MAX_SECONDS) {
            withFrameNanos { now ->
                owed += min((now - last) / NANOS_PER_SECOND, MAX_FRAME_SECONDS)
                last = now
                val tilt = slope().coerceIn(-MAX_SLOPE, MAX_SLOPE)
                while (owed >= WaterSim.DT && !done) {
                    val level = target(seconds)
                    if (level == null) done = true else water.step(level, tilt)
                    owed -= WaterSim.DT
                    seconds += WaterSim.DT
                }
                frame++
            }
        }
    }

    private suspend fun crossfade(mine: Int, to: Float, end: Phase) {
        sim = null
        fade.snapTo(1f - to)
        phase = Phase.FADING
        fade.animateTo(to, tween(REDUCED_FADE_MS))
        if (mine == run) phase = end
    }

    internal enum class Phase { EMPTY, RISING, DRAINING, FADING, FULL }

    internal companion object {
        const val DESIGN_WIDTH = 540f
        const val DESIGN_HEIGHT = 1170f
        const val NANOS_PER_SECOND = 1e9f

        /** A late frame counts as two at most, so a stall slows the water instead of skipping it. */
        const val MAX_FRAME_SECONDS = 1f / 30f
        const val MAX_SECONDS = 2.5f
        const val MAX_SLOPE = 0.45f

        const val TAP_IMPULSE = -1500f
        const val DRAIN_IMPULSE = -600f
        const val BRIM_LEVEL = 14f
        const val BRIM_NEAR = 60f
        const val BRIM_HOLD_SECONDS = 0.26f
        const val OVERFILL_LEVEL = -90f
        const val COVERED_LEVEL = -60f
        const val DRAIN_TARGET_BELOW = 120f
        const val DRAINED_BELOW = 40f
        const val REDUCED_FADE_MS = 150
    }
}

/**
 * Shows this content only under the surface of [motion] (design motion v3, #107). Only a path
 * clip, fills and strokes, so every API level (the Android 11 A50 too) looks the same.
 *
 * [cover] (the screen's own background) hides what is drawn on it and fades with the level: in
 * between 50 % and 95 % of the way up, out over the first 30 % of the way down (#106). [glint]
 * scales the lighter band under the waterline.
 */
fun Modifier.water(motion: WaterMotion, cover: Brush? = null, glint: Float = 1f): Modifier = this
    .onSizeChanged { motion.size = it }
    .graphicsLayer { alpha = if (motion.phase == WaterMotion.Phase.FADING) motion.fade.value else 1f }
    .drawWithContent {
        val sim = motion.sim
        when (motion.phase) {
            WaterMotion.Phase.EMPTY -> Unit
            WaterMotion.Phase.FULL, WaterMotion.Phase.FADING -> drawContent()
            WaterMotion.Phase.RISING, WaterMotion.Phase.DRAINING -> {
                // Reading the frame counter redraws this (and nothing else) every simulated frame.
                motion.frame
                if (sim != null) drawUnderSurface(sim, motion.phase == WaterMotion.Phase.RISING, cover, glint)
            }
        }
    }

private fun ContentDrawScope.drawUnderSurface(sim: WaterSim, rising: Boolean, cover: Brush?, glint: Float) {
    val scope = this
    val scale = size.width / sim.width
    val tops = FloatArray(WaterSim.COLUMNS).also(sim::surface)
    val last = WaterSim.COLUMNS - 1
    val step = size.width / last
    // A smooth curve through the column tops: each top is a control point, each midpoint an end.
    val line = Path()
    line.moveTo(-step, tops[0] * scale)
    line.lineTo(0f, tops[0] * scale)
    for (i in 1..last) {
        val x0 = (i - 1) * step
        val y0 = tops[i - 1] * scale
        val y1 = tops[i] * scale
        line.quadraticTo(x0, y0, x0 + step / 2f, (y0 + y1) / 2f)
    }
    line.lineTo(size.width, tops[last] * scale)
    line.lineTo(size.width + step, tops[last] * scale)
    val body = Path().apply {
        addPath(line)
        lineTo(size.width + step, size.height)
        lineTo(-step, size.height)
        close()
    }
    val filled = 1f - (sim.level / sim.height).coerceIn(0f, 1f)
    val shown = if (rising) {
        smoothstep(CONTENT_IN_FROM, CONTENT_IN_TO, filled)
    } else {
        smoothstep(CONTENT_OUT_BY, 1f, filled)
    }
    clipPath(body) {
        scope.drawContent()
        if (cover != null && shown < 1f) drawRect(cover, alpha = 1f - shown)
        // Matte waterline: a soft lighter band just under the surface, no gloss. Two strokes; their
        // upper halves are clipped away.
        val band = BAND_ALPHA * glint / 2
        drawPath(line, Color.White.copy(alpha = band), style = Stroke(width = 28f * scale))
        drawPath(line, Color.White.copy(alpha = band), style = Stroke(width = 12f * scale))
    }
}

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val u = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return u * u * (3 - 2 * u)
}

private const val BAND_ALPHA = 0.10f
private const val CONTENT_IN_FROM = 0.50f
private const val CONTENT_IN_TO = 0.95f
private const val CONTENT_OUT_BY = 0.70f
