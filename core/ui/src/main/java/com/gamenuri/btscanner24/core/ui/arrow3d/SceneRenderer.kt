package com.gamenuri.btscanner24.core.ui.arrow3d

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the find arrow as a real 3D object on a floor (#76): the mesh with a matte two-tone light
 * and a faint blue rim, a planar shadow, a floor ring and the confidence sector, seen through a
 * perspective camera that follows how the phone is held. Within reach ("right here") the arrow
 * shrinks away, a mint puck takes its place and rings pulse out over the same floor; "Found it"
 * is a check medal rising from that floor (design find-flow-3d v1.1). As a layer across the screen
 * the floor carries on past the ring: two more rings and the device marker far out. OpenGL ES 2.0
 * only, so every test phone (the Android 11 A50 too) runs the same shaders.
 *
 * The scene is heading-relative: −Z is where the phone points, +X its right, +Y up. All easing
 * happens here on the GL thread, so the UI thread only sets targets in [SceneState].
 */
internal class SceneRenderer(private val mesh: FloatArray, private val state: SceneState) {
    private val puck = DiscMesh.build(PUCK_RADIUS, PUCK_THICKNESS, PUCK_BEVEL)
    private val medal = DiscMesh.build(MEDAL_RADIUS, MEDAL_THICKNESS, MEDAL_BEVEL)
    private val check = CheckMesh.build(base = MEDAL_THICKNESS / 2)

    private var arrowProgram = 0
    private var flatProgram = 0
    private var meshBuffer = 0
    private var ringBuffer = 0
    private var sectorBuffer = 0
    private var puckBuffer = 0
    private var medalBuffer = 0
    private var checkBuffer = 0
    private var pulseBuffer = 0
    private var discBuffer = 0
    private var thinBuffer = 0
    private var dashBuffer = 0
    private var haloBuffer = 0
    private val meshVertices = mesh.size / ObjMesh.FLOATS_PER_VERTEX
    private val puckVertices = puck.size / ObjMesh.FLOATS_PER_VERTEX
    private val medalVertices = medal.size / ObjMesh.FLOATS_PER_VERTEX
    private val checkVertices = check.size / ObjMesh.FLOATS_PER_VERTEX

    private val projection = FloatArray(16)
    private val lookAt = FloatArray(16)
    private val rollMatrix = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val model = FloatArray(16)
    private val markerModel = FloatArray(16)
    private val shadowModel = FloatArray(16)
    private val mvp = FloatArray(16)
    private val shadow = FloatArray(16)
    private val probe = FloatArray(4)
    private val projected = FloatArray(4)
    private var viewportWidth = 1f
    private var viewportHeight = 1f

    // Eased values.
    private var initialized = false
    private var time = 0f
    private var heading = 0f
    private var elevation = SceneCamera.TYPICAL
    private var roll = 0f
    private var bearing = 0f
    private var bearingVelocity = 0f
    private var spin = 0f
    private var wasSpinning = false
    private var width = 1f
    private var sectorHalf = SECTOR_NARROW

    /** 0 = the arrow, 1 = the "right here" puck. */
    private var here = 0f

    /** 1 = no arrow at all (no signal to point at). */
    private var gone = 0f

    /** Seconds since the medal started to rise. */
    private var foundTime = 0f

    /** 0..1: how much of the device marker on the far ring shows. */
    private var marker = 0f

    fun onSurfaceCreated() {
        arrowProgram = program(ARROW_VERTEX, ARROW_FRAGMENT)
        flatProgram = program(FLAT_VERTEX, FLAT_FRAGMENT)
        val buffers = IntArray(BUFFERS)
        GLES20.glGenBuffers(BUFFERS, buffers, 0)
        meshBuffer = buffers[0]
        ringBuffer = buffers[1]
        sectorBuffer = buffers[2]
        puckBuffer = buffers[3]
        medalBuffer = buffers[4]
        checkBuffer = buffers[5]
        pulseBuffer = buffers[6]
        discBuffer = buffers[7]
        thinBuffer = buffers[8]
        dashBuffer = buffers[9]
        haloBuffer = buffers[10]
        upload(meshBuffer, mesh)
        upload(puckBuffer, puck)
        upload(medalBuffer, medal)
        upload(checkBuffer, check)
        upload(ringBuffer, ringStrip(RING_INNER, RING_OUTER))
        upload(pulseBuffer, ringStrip(1f - ACCENT_RING_WIDTH, 1f))
        upload(discBuffer, ringStrip(0f, 1f))
        upload(thinBuffer, ringStrip(1f - THIN_RING_WIDTH, 1f))
        upload(dashBuffer, dashedRing())
        upload(haloBuffer, ringStrip(TARGET_HALO_INNER, 1f))
        upload(sectorBuffer, sectorTriangles())
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        // Premultiplied alpha: the layer under this view (the find screen's blue) shows through.
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth = width.coerceAtLeast(1).toFloat()
        viewportHeight = height.coerceAtLeast(1).toFloat()
    }

    fun draw(deltaSeconds: Float) {
        val yaw = advance(deltaSeconds.coerceIn(0f, MAX_STEP_SECONDS))
        camera()
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_STENCIL_BUFFER_BIT)

        if (state.found) {
            drawFound()
            return
        }
        val bob = if (state.reduceMotion) 0f else HOVER_BOB * sin(time * TAU / HOVER_PERIOD_SECONDS)
        val hover = HOVER_HEIGHT + bob
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, 0f, hover, 0f)
        Matrix.rotateM(model, 0, -yaw, 0f, 1f, 0f)
        val arrowScale = (1f - here) * (1f - gone) * MESH_SCALE
        Matrix.scaleM(model, 0, width * arrowScale, arrowScale, arrowScale)
        Matrix.setIdentityM(markerModel, 0)
        Matrix.translateM(markerModel, 0, 0f, PUCK_HEIGHT + bob, 0f)
        Matrix.scaleM(markerModel, 0, here * MESH_SCALE, here * MESH_SCALE, here * MESH_SCALE)
        val showArrow = here < 1f - HERE_EPSILON && gone < 1f - HERE_EPSILON
        val showPuck = here > HERE_EPSILON

        drawFloor(yaw)
        // Both shadows first (blended onto the floor), then the solids.
        if (showArrow) drawShadow(meshBuffer, meshVertices, model)
        if (showPuck) drawShadow(puckBuffer, puckVertices, markerModel)
        if (showArrow) drawSolid(meshBuffer, meshVertices, model, state.arrowColor, stretch = width)
        if (showPuck) drawSolid(puckBuffer, puckVertices, markerModel, state.accentColor)
    }

    /**
     * "Found it": the medal rises from the floor ring to its place while it turns a little more
     * than once and grows, then hovers. With animations off it is simply there.
     */
    private fun drawFound() {
        val rise = if (state.reduceMotion) 1f else (foundTime / MEDAL_RISE_SECONDS).coerceIn(0f, 1f)
        val left = 1f - rise
        val eased = 1f - left * left * left
        val bob = if (state.reduceMotion || rise < 1f) {
            0f
        } else {
            HOVER_BOB * sin((foundTime - MEDAL_RISE_SECONDS) * TAU / HOVER_PERIOD_SECONDS)
        }
        val scale = (MEDAL_START_SCALE + (1f - MEDAL_START_SCALE) * eased) * MESH_SCALE
        Matrix.setIdentityM(markerModel, 0)
        Matrix.translateM(markerModel, 0, 0f, MEDAL_HEIGHT * eased + bob, 0f)
        Matrix.rotateM(markerModel, 0, (1f - eased) * MEDAL_SPIN_DEGREES, 0f, 1f, 0f)
        // Stood up to face the viewer, leaning back a little.
        Matrix.rotateM(markerModel, 0, MEDAL_TILT_DEGREES, 1f, 0f, 0f)
        Matrix.scaleM(markerModel, 0, scale, scale, scale)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glUseProgram(flatProgram)
        val position = GLES20.glGetAttribLocation(flatProgram, "aPos")
        GLES20.glEnableVertexAttribArray(position)
        val ringVertices = (RING_SEGMENTS + 1) * 2
        drawBaseRings(position, ringVertices)
        val accent = state.accentColor
        Matrix.scaleM(mvp, 0, viewProjection, 0, FOUND_RING, 1f, FOUND_RING)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, discBuffer)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, 0)
        flat(mvp, accent, FOUND_DISC_ALPHA)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, ringVertices)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, pulseBuffer)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, 0)
        flat(mvp, accent, 1f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, ringVertices)
        GLES20.glDisableVertexAttribArray(position)

        drawShadow(medalBuffer, medalVertices, markerModel)
        drawSolid(medalBuffer, medalVertices, markerModel, state.arrowColor)
        drawSolid(checkBuffer, checkVertices, markerModel, accent)
    }

    /** Moves every eased value one step and returns the arrow's yaw (clockwise from "ahead"). */
    private fun advance(dt: Float): Float {
        time += dt
        foundTime = if (state.found) foundTime + dt else 0f
        val world = state.worldBearingDegrees
        val anchored = !world.isNaN() && state.hasPose
        val target = if (anchored) world else state.relativeDegrees
        val targetElevation =
            if (state.hasPose) SceneCamera.elevationFor(state.elevationDegrees) else SceneCamera.TYPICAL
        // Lying flat, a sideways tilt is not a roll of the view; fade it out towards the top view.
        val targetRoll = if (state.hasPose) {
            state.rollDegrees.coerceIn(-MAX_ROLL, MAX_ROLL) * cos(radians(targetElevation))
        } else {
            0f
        }
        if (!initialized) {
            initialized = true
            heading = state.headingDegrees
            elevation = targetElevation
            roll = targetRoll
            bearing = target
        }
        val fast = 1f - exp(-dt / POSE_SMOOTH_SECONDS)
        val slow = 1f - exp(-dt / SHAPE_SMOOTH_SECONDS)
        heading += wrap(state.headingDegrees - heading) * fast
        elevation += (targetElevation - elevation) * fast
        roll += (targetRoll - roll) * fast
        width += ((if (state.wide) WIDE_SCALE else 1f) - width) * slow
        val targetSector = when {
            state.locked -> SECTOR_LOCKED
            state.wide -> SECTOR_WIDE
            else -> SECTOR_NARROW
        }
        sectorHalf += (targetSector - sectorHalf) * slow
        val targetMarker = if (state.showTarget && !state.spinning) 1f else 0f
        marker += (targetMarker - marker) * slow
        val targetHere = if (state.rightHere) 1f else 0f
        here = if (state.reduceMotion) targetHere else here + (targetHere - here) * slow
        val targetGone = if (state.noArrow) 1f else 0f
        gone = if (state.reduceMotion) targetGone else gone + (targetGone - gone) * slow

        val spinning = state.spinning
        if (spinning) {
            spin = (spin + SPIN_DEGREES_PER_SECOND * dt) % 360f
        } else if (wasSpinning) {
            // Carry on from where the spin stopped, so the arrow swings to its direction.
            bearing = if (anchored) spin + heading else spin
            bearingVelocity = SPIN_DEGREES_PER_SECOND
        }
        wasSpinning = spinning

        // Damped spring towards the direction; under-damped (a small overshoot) once it is locked.
        val damping = if (state.locked) LOCKED_DAMPING else 1f
        val offset = wrap(bearing - target)
        val acceleration = -SPRING_OMEGA * SPRING_OMEGA * offset - 2f * damping * SPRING_OMEGA * bearingVelocity
        bearingVelocity += acceleration * dt
        bearing += bearingVelocity * dt

        return when {
            spinning -> spin
            anchored -> bearing - heading
            else -> bearing
        }
    }

    private fun camera() {
        // One floor unit is the same number of pixels whatever the view's shape: the ring is
        // [SceneState.ringFraction] of the width, and a wider or taller view only sees more floor.
        // The floor's middle sits at [SceneState.centerY] of the height, not always in the centre.
        val unitsPerPixel = RING_OUTER / ringPixels()
        val nearPerPixel = unitsPerPixel * NEAR / CAMERA_DISTANCE
        val above = state.centerY.coerceIn(0f, 1f) * viewportHeight
        Matrix.frustumM(
            projection,
            0,
            -nearPerPixel * viewportWidth / 2,
            nearPerPixel * viewportWidth / 2,
            -nearPerPixel * (viewportHeight - above),
            nearPerPixel * above,
            NEAR,
            FAR,
        )
        val e = radians(elevation)
        Matrix.setLookAtM(
            lookAt, 0,
            0f, TARGET_HEIGHT + CAMERA_DISTANCE * sin(e), CAMERA_DISTANCE * cos(e),
            0f, TARGET_HEIGHT, 0f,
            0f, 1f, 0f,
        )
        // The floor stays level when the phone leans sideways.
        Matrix.setRotateM(rollMatrix, 0, -roll, 0f, 0f, 1f)
        Matrix.multiplyMM(view, 0, rollMatrix, 0, lookAt, 0)
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
    }

    /**
     * The floor ring's radius in pixels: [SceneState.ringFraction] of the width, or less when the
     * place the layout keeps free (between the two fades) is too low for it. The steeper the camera
     * looks down (phone lying flat), the taller the ring is on screen and the more it has to shrink,
     * so it never reaches the state text above or the distance below (#121).
     */
    private fun ringPixels(): Float {
        val wanted = state.ringFraction.coerceAtLeast(MIN_RING_FRACTION) * viewportWidth
        val top = state.topFade
        val bottom = state.bottomFade
        val center = state.centerY
        if (top < 0f || bottom < 0f || center <= top || bottom <= center) return wanted
        val e = radians(elevation)
        // Per pixel of R: how far the arrow's tip and top reach above the middle, the ring's near edge below.
        val above = ARROW_REACH * sin(e) + ARROW_TOP * cos(e)
        // The steeper the view, the more the near edge grows with perspective (#130: lying flat it came
        // within 10 px of the distance label).
        val steeper = (sin(e) - sin(radians(SceneCamera.TYPICAL))).coerceAtLeast(0f)
        val below = (NEAR_EDGE + NEAR_EDGE_STEEP * steeper) * sin(e)
        val room = FIT_MARGIN * viewportHeight
        val fits = minOf((center - top) * room / above, (bottom - center) * room / below)
        return minOf(wanted, fits).coerceAtLeast(MIN_RING_FRACTION * viewportWidth)
    }

    /**
     * How far out the device marker sits: on the far ring, or closer in when that would put it on
     * the state text. It comes in only as far as it must, so it stays ahead of the arrow's tip, and
     * it is drawn whole there instead of fading with the far floor (#121).
     */
    private fun targetRadius(x: Float, z: Float): Float {
        val top = state.topFade
        if (top < 0f) return FAR_RING
        // Normalised height (−1 bottom, +1 top) of the highest place for the marker's middle.
        val limit = 1f - 2f * (top + TARGET_CLEARANCE)
        var radius = FAR_RING
        while (radius > TARGET_MIN_RADIUS) {
            probe[0] = x * radius
            probe[1] = 0f
            probe[2] = z * radius
            probe[3] = 1f
            Matrix.multiplyMV(projected, 0, viewProjection, 0, probe, 0)
            if (projected[3] > 0f && projected[1] / projected[3] <= limit) return radius
            radius -= TARGET_STEP
        }
        return TARGET_MIN_RADIUS
    }

    private fun drawFloor(yaw: Float) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glUseProgram(flatProgram)
        val position = GLES20.glGetAttribLocation(flatProgram, "aPos")
        val color = state.floorColor

        GLES20.glEnableVertexAttribArray(position)
        val ringVertices = (RING_SEGMENTS + 1) * 2
        drawBaseRings(position, ringVertices)
        if (state.spinning) {
            // Sweep progress, clockwise from straight ahead.
            val segments = (state.progress.coerceIn(0f, 1f) * RING_SEGMENTS).roundToInt()
            if (segments > 0) {
                flat(viewProjection, color, 1f)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, (segments + 1) * 2)
            }
        } else if (here < 1f - HERE_EPSILON && !state.noArrow) {
            // "Somewhere this way": the sector turns with the arrow and widens as confidence drops.
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sectorBuffer)
            GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, 0)
            Matrix.rotateM(mvp, 0, viewProjection, 0, -yaw, 0f, 1f, 0f)
            if (state.fullBleed) {
                // Across the screen the sector reaches the far ring, evenly lit, and fades with it.
                Matrix.scaleM(mvp, 0, FAR_SECTOR_SCALE, 1f, FAR_SECTOR_SCALE)
                flat(mvp, color, FAR_SECTOR_ALPHA * (1f - here), soft = true)
            } else {
                flat(mvp, color, SECTOR_ALPHA * (1f - here), radial = true)
            }
            val half = sectorHalf.roundToInt().coerceIn(1, SECTOR_MAX_HALF)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, (SECTOR_MAX_HALF - half) * 3, half * 2 * 3)
        }
        if (state.fullBleed && marker * (1f - here) > HERE_EPSILON) drawTarget(position, yaw, ringVertices)
        if (here > HERE_EPSILON) drawPulses(position, ringVertices)
        GLES20.glDisableVertexAttribArray(position)
    }

    /**
     * The device as a mint dot with a ring on the far floor ring, where the arrow points: it is
     * what the arrow is pointing at. Behind the viewer it slides under the fade and is not seen.
     */
    private fun drawTarget(position: Int, yaw: Float, ringVertices: Int) {
        val (x, z) = floorDirection(yaw)
        val color = state.accentColor
        val alpha = marker * (1f - here)
        val radius = targetRadius(x, z)
        Matrix.translateM(shadowModel, 0, viewProjection, 0, x * radius, 0f, z * radius)
        Matrix.scaleM(mvp, 0, shadowModel, 0, TARGET_DOT, 1f, TARGET_DOT)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, discBuffer)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, 0)
        flat(mvp, color, alpha, soft = true, fadeTop = false)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, ringVertices)
        Matrix.scaleM(mvp, 0, shadowModel, 0, TARGET_HALO, 1f, TARGET_HALO)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, haloBuffer)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, 0)
        flat(mvp, color, alpha * TARGET_HALO_ALPHA, soft = true, fadeTop = false)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, ringVertices)
    }

    /**
     * The floor ring. In a box a second, fainter ring closer in gives the floor some depth; across
     * the whole screen (design find-flow-3d v1.1) the ring is brighter and lightly filled, and the
     * floor goes on: a ring further out and a dashed one far away, fading before the distance number.
     */
    private fun drawBaseRings(position: Int, ringVertices: Int) {
        val color = state.floorColor
        val wide = state.fullBleed
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ringBuffer)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, 0)
        flat(viewProjection, color, if (wide) WIDE_RING_ALPHA else RING_ALPHA)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, ringVertices)
        if (!wide) {
            Matrix.scaleM(mvp, 0, viewProjection, 0, INNER_RING, 1f, INNER_RING)
            flat(mvp, color, INNER_RING_ALPHA)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, ringVertices)
            return
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, discBuffer)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, 0)
        Matrix.scaleM(mvp, 0, viewProjection, 0, RING_INNER, 1f, RING_INNER)
        flat(mvp, color, RING_FILL_ALPHA)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, ringVertices)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, thinBuffer)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, 0)
        Matrix.scaleM(mvp, 0, viewProjection, 0, MID_RING, 1f, MID_RING)
        flat(mvp, color, MID_RING_ALPHA, soft = true)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, ringVertices)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dashBuffer)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, 0)
        Matrix.scaleM(mvp, 0, viewProjection, 0, FAR_RING, 1f, FAR_RING)
        flat(mvp, color, FAR_RING_ALPHA, soft = true)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, DASHES * 2 * 3)
        // The sweep progress and the sector are drawn from the ring buffer again.
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ringBuffer)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, 0)
    }

    /**
     * "Right here": mint rings spread from the puck far out over the floor, fading as they go. They
     * also fade towards the edges of the view and below the middle of the screen, so they never
     * cross the distance number or the buttons.
     */
    private fun drawPulses(position: Int, ringVertices: Int) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, pulseBuffer)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, 0)
        val color = state.accentColor
        if (state.reduceMotion) {
            // Animations off: two rings that stand still.
            for (ring in STILL_RINGS.indices) {
                Matrix.scaleM(mvp, 0, viewProjection, 0, STILL_RINGS[ring], 1f, STILL_RINGS[ring])
                flat(mvp, color, STILL_ALPHAS[ring] * here, soft = true)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, ringVertices)
            }
            return
        }
        for (ring in 0 until PULSE_RINGS) {
            val spread = (time / PULSE_PERIOD_SECONDS + ring.toFloat() / PULSE_RINGS) % 1f
            val radius = PULSE_FROM + (PULSE_TO - PULSE_FROM) * spread
            Matrix.scaleM(mvp, 0, viewProjection, 0, radius, 1f, radius)
            flat(mvp, color, PULSE_ALPHA * (1f - spread) * here, soft = true)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, ringVertices)
        }
    }

    /**
     * A mesh dropped straight onto the floor, nudged towards the viewer. Three faint copies of
     * growing size stand in for a blur; the stencil keeps each copy from doubling up on itself.
     */
    private fun drawShadow(buffer: Int, vertices: Int, model: FloatArray) {
        GLES20.glUseProgram(flatProgram)
        val position = GLES20.glGetAttribLocation(flatProgram, "aPos")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 0)
        GLES20.glEnable(GLES20.GL_STENCIL_TEST)
        GLES20.glStencilOp(GLES20.GL_KEEP, GLES20.GL_KEEP, GLES20.GL_INCR)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(flatProgram, "uRadial"), 0f)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(flatProgram, "uEdge"), 0f)
        GLES20.glUniform4f(
            GLES20.glGetUniformLocation(flatProgram, "uColor"),
            SHADOW_RED * SHADOW_LAYER_ALPHA,
            SHADOW_GREEN * SHADOW_LAYER_ALPHA,
            SHADOW_BLUE * SHADOW_LAYER_ALPHA,
            SHADOW_LAYER_ALPHA,
        )
        GLES20.glStencilFunc(GLES20.GL_EQUAL, 0, 0xFF)
        for (scale in SHADOW_SCALES) {
            // A fresh stencil per copy: each one covers its own footprint exactly once.
            GLES20.glClear(GLES20.GL_STENCIL_BUFFER_BIT)
            Matrix.setIdentityM(shadow, 0)
            Matrix.translateM(shadow, 0, 0f, 0f, SHADOW_OFFSET)
            // Height scaled to zero: straight down onto the floor, wherever the arrow hovers.
            Matrix.scaleM(shadow, 0, scale, 0f, scale)
            Matrix.multiplyMM(shadowModel, 0, shadow, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, viewProjection, 0, shadowModel, 0)
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(flatProgram, "uMvp"), 1, false, mvp, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertices)
        }
        GLES20.glDisable(GLES20.GL_STENCIL_TEST)
        GLES20.glDisableVertexAttribArray(position)
    }

    /** A lit mesh; [stretch] is how much wider than its model it is drawn (the low-confidence arrow). */
    private fun drawSolid(buffer: Int, vertices: Int, model: FloatArray, color: Int, stretch: Float = 1f) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(arrowProgram)
        val position = GLES20.glGetAttribLocation(arrowProgram, "aPos")
        val normal = GLES20.glGetAttribLocation(arrowProgram, "aNormal")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glEnableVertexAttribArray(normal)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 0)
        GLES20.glVertexAttribPointer(normal, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 3 * BYTES_PER_FLOAT)

        Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(arrowProgram, "uMvp"), 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(arrowProgram, "uModel"), 1, false, model, 0)
        // Normals of a mesh stretched sideways: divide by the stretch twice (inverse transpose).
        GLES20.glUniform3f(GLES20.glGetUniformLocation(arrowProgram, "uInvScale"), 1f / (stretch * stretch), 1f, 1f)
        GLES20.glUniform3f(
            GLES20.glGetUniformLocation(arrowProgram, "uBase"),
            red(color) * BASE_BRIGHTNESS,
            green(color) * BASE_BRIGHTNESS,
            blue(color) * BASE_BRIGHTNESS,
        )
        GLES20.glUniform3f(GLES20.glGetUniformLocation(arrowProgram, "uLight"), LIGHT[0], LIGHT[1], LIGHT[2])
        val e = radians(elevation)
        GLES20.glUniform3f(
            GLES20.glGetUniformLocation(arrowProgram, "uCamera"),
            0f,
            TARGET_HEIGHT + CAMERA_DISTANCE * sin(e),
            CAMERA_DISTANCE * cos(e),
        )
        GLES20.glUniform1f(GLES20.glGetUniformLocation(arrowProgram, "uGloss"), GLOSS)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertices)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glDisableVertexAttribArray(normal)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    /** [soft]: fades towards the edges of the view and below the middle of the screen (the pulses). */
    private fun flat(
        matrix: FloatArray,
        color: Int,
        alpha: Float,
        radial: Boolean = false,
        soft: Boolean = false,
        fadeTop: Boolean = true,
    ) {
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(flatProgram, "uMvp"), 1, false, matrix, 0)
        // Across the whole screen the floor runs on to the edges; in a box it softens towards them.
        val edge = if (state.fullBleed) NO_EDGE_FADE else EDGE_FADE
        GLES20.glUniform1f(GLES20.glGetUniformLocation(flatProgram, "uEdge"), if (soft) edge else 0f)
        if (soft) {
            GLES20.glUniform2f(GLES20.glGetUniformLocation(flatProgram, "uViewport"), viewportWidth, viewportHeight)
            // From below: gone where the distance label starts when the layout says where that is,
            // else between 50 % and 60 % of the screen; without either (not laid out yet) no fade.
            val bottom = state.bottomFade
            val fades = state.fadeFullY > state.fadeClearY
            val goneY = when {
                bottom >= 0f -> (1f - bottom) * viewportHeight
                fades -> state.fadeClearY
                else -> -2f
            }
            val wholeY = when {
                bottom >= 0f -> goneY + FADE_BAND * viewportHeight
                fades -> state.fadeFullY
                else -> -1f
            }
            GLES20.glUniform2f(GLES20.glGetUniformLocation(flatProgram, "uFade"), goneY, wholeY)
            // From the top: gone above the state text's lower edge, whole a little below it.
            val top = if (fadeTop) state.topFade else -1f
            val clearY = if (top >= 0f) (1f - top) * viewportHeight else NO_TOP_FADE
            GLES20.glUniform2f(
                GLES20.glGetUniformLocation(flatProgram, "uTop"),
                clearY - FADE_BAND * viewportHeight,
                clearY,
            )
        }
        // Radial: clear at the centre, full colour at the rim (the direction sector).
        GLES20.glUniform1f(GLES20.glGetUniformLocation(flatProgram, "uRadial"), if (radial) 1f / SECTOR_RADIUS else 0f)
        GLES20.glUniform4f(
            GLES20.glGetUniformLocation(flatProgram, "uColor"),
            red(color) * alpha,
            green(color) * alpha,
            blue(color) * alpha,
            alpha,
        )
    }

    private fun upload(buffer: Int, data: FloatArray) {
        val bytes = ByteBuffer.allocateDirect(data.size * BYTES_PER_FLOAT).order(ByteOrder.nativeOrder())
        bytes.asFloatBuffer().put(data)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.size * BYTES_PER_FLOAT, bytes, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun program(vertex: String, fragment: String): Int {
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, shader(GLES20.GL_VERTEX_SHADER, vertex))
        GLES20.glAttachShader(program, shader(GLES20.GL_FRAGMENT_SHADER, fragment))
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "link: " + GLES20.glGetProgramInfoLog(program) }
        return program
    }

    private fun shader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "compile: " + GLES20.glGetShaderInfoLog(shader) }
        return shader
    }

    private companion object {
        const val BYTES_PER_FLOAT = 4
        const val STRIDE_BYTES = ObjMesh.FLOATS_PER_VERTEX * BYTES_PER_FLOAT
        const val TAU = (2 * PI).toFloat()
        const val MAX_STEP_SECONDS = 0.05f

        // Camera.
        const val MIN_RING_FRACTION = 0.05f

        // Fitting the ring between the texts (#121), in units of R: the arrow's tip is 0.75 ahead of
        // the middle and its top face 0.36 above the floor; the ring's near edge looks about 8 %
        // bigger than R because it is closer to the camera.
        const val ARROW_REACH = 0.75f
        const val ARROW_TOP = 0.36f
        const val NEAR_EDGE = 1.08f
        const val NEAR_EDGE_STEEP = 0.5f
        const val FIT_MARGIN = 0.94f
        const val NEAR = 0.5f
        const val FAR = 30f
        const val CAMERA_DISTANCE = 6.6f
        const val TARGET_HEIGHT = 0.12f
        const val MAX_ROLL = 30f
        const val POSE_SMOOTH_SECONDS = 0.09f

        // Ring radius R; declared first because the arrow constants below use it.
        const val RING_OUTER = 1.36f
        const val RING_INNER = RING_OUTER - 0.026f

        // Arrow.
        // Design #76, in units of the ring radius R (RING_OUTER). [ArrowMesh] is in R already; its
        // underside floats 0.22 R above the floor (centre 0.29 R), bobbing by ± 0.02 R.
        const val MESH_SCALE = RING_OUTER
        const val HOVER_HEIGHT = 0.29f * RING_OUTER
        const val HOVER_BOB = 0.027f
        const val HOVER_PERIOD_SECONDS = 2.4f
        const val WIDE_SCALE = 1.4f
        const val SHAPE_SMOOTH_SECONDS = 0.15f
        const val SPIN_DEGREES_PER_SECOND = 150f
        const val SPRING_OMEGA = 10f
        const val LOCKED_DAMPING = 0.45f
        const val BASE_BRIGHTNESS = 1f
        const val GLOSS = 0.10f

        // Floor.
        const val RING_SEGMENTS = 96

        // Outer ring about 2 dp wide at 35 %, inner ring at 0.62 R about 1 dp at 18 % (R = 108 dp).
        const val INNER_RING = 0.62f
        const val RING_ALPHA = 0.35f
        const val INNER_RING_ALPHA = 0.18f
        const val SECTOR_RADIUS = RING_INNER
        const val SECTOR_MAX_HALF = 45
        const val SECTOR_LOCKED = 12f
        const val SECTOR_NARROW = 20f
        const val SECTOR_WIDE = 35f
        const val SECTOR_ALPHA = 0.22f

        // "Right here" (design find-flow-3d v1.1, in R): a mint puck of radius 0.34 and thickness
        // 0.11 floating at 0.24; three mint rings spread from 0.55 to 3.1 in 1.8 s, 85 % → 0.
        const val PUCK_RADIUS = 0.34f
        const val PUCK_THICKNESS = 0.11f
        const val PUCK_BEVEL = 0.02f
        const val PUCK_HEIGHT = 0.24f * RING_OUTER
        const val PULSE_RINGS = 3
        const val PULSE_PERIOD_SECONDS = 1.8f
        const val PULSE_FROM = 0.55f * RING_OUTER
        const val PULSE_TO = 3.1f * RING_OUTER
        const val PULSE_ALPHA = 0.85f
        val STILL_RINGS = floatArrayOf(0.8f * RING_OUTER, 1.2f * RING_OUTER)
        val STILL_ALPHAS = floatArrayOf(0.85f, 0.45f)
        const val EDGE_FADE = 0.12f
        const val NO_EDGE_FADE = 0.0001f
        const val NO_TOP_FADE = 12_000f
        const val FADE_BAND = 0.06f

        // Full-bleed floor (design find-flow-3d v1.1, the designer's scene values): the ring at
        // 45 % with a 7 % fill, a ring at 1.76 R (30 %), a dashed one at 2.64 R (48 dashes, 55 %
        // drawn, 22 %) that carries the mint device marker (dot 0.13 R, ring 0.18–0.21 R at 60 %),
        // and the sector out to the dashed ring at 13 %.
        const val WIDE_RING_ALPHA = 0.45f
        const val RING_FILL_ALPHA = 0.07f
        const val THIN_RING_WIDTH = 0.008f
        const val MID_RING = 1.76f * RING_OUTER
        const val MID_RING_ALPHA = 0.30f
        const val FAR_RING = 2.64f * RING_OUTER
        const val FAR_RING_ALPHA = 0.22f
        const val DASHES = 48
        const val DASH_DUTY = 0.55f
        const val FAR_SECTOR_SCALE = FAR_RING / SECTOR_RADIUS
        const val FAR_SECTOR_ALPHA = 0.13f
        const val TARGET_DOT = 0.13f * RING_OUTER
        const val TARGET_HALO = 0.21f * RING_OUTER
        const val TARGET_HALO_INNER = 0.18f / 0.21f
        const val TARGET_HALO_ALPHA = 0.6f
        const val TARGET_MIN_RADIUS = 1.25f * RING_OUTER
        const val TARGET_STEP = 0.05f * RING_OUTER
        const val TARGET_CLEARANCE = 0.04f
        const val HERE_EPSILON = 0.01f

        // "Found it" (same design): a white medal of radius 0.6 and thickness 0.13 with rounded
        // edges rises to 0.75 in 0.8 s, turning a little more than once and growing from 0.4; on
        // the floor a mint ring at 1.25 (0.07 thick) around a 16 % mint disc.
        const val MEDAL_RADIUS = 0.6f
        const val MEDAL_THICKNESS = 0.13f
        const val MEDAL_BEVEL = 0.045f
        const val MEDAL_HEIGHT = 0.75f * RING_OUTER
        const val MEDAL_RISE_SECONDS = 0.8f
        const val MEDAL_SPIN_DEGREES = 400f
        const val MEDAL_TILT_DEGREES = 72f
        const val MEDAL_START_SCALE = 0.4f
        const val FOUND_RING = 1.25f * RING_OUTER
        const val FOUND_DISC_ALPHA = 0.16f
        const val ACCENT_RING_WIDTH = 0.07f / 1.25f
        const val BUFFERS = 11

        // Shadow: #0B2A8A, never black, about 28 % where all three copies overlap.
        const val SHADOW_RED = 0.043f
        const val SHADOW_GREEN = 0.165f
        const val SHADOW_BLUE = 0.541f
        const val SHADOW_LAYER_ALPHA = 0.10f
        const val SHADOW_OFFSET = 0.068f
        val SHADOW_SCALES = floatArrayOf(1.00f, 1.06f, 1.12f)

        /** Towards the light: up, from the left, a little towards the viewer. */
        val LIGHT = normalized(-0.45f, 1f, 0.5f)

        fun normalized(x: Float, y: Float, z: Float): FloatArray {
            val length = sqrt(x * x + y * y + z * z)
            return floatArrayOf(x / length, y / length, z / length)
        }

        fun radians(degrees: Float): Float = degrees * PI.toFloat() / 180f

        /** Into −180..180. */
        fun wrap(degrees: Float): Float = ((degrees % 360f) + 540f) % 360f - 180f

        fun red(color: Int): Float = (color shr 16 and 0xFF) / 255f

        fun green(color: Int): Float = (color shr 8 and 0xFF) / 255f

        fun blue(color: Int): Float = (color and 0xFF) / 255f

        /** Direction on the floor at [degrees] clockwise from straight ahead (−Z). */
        fun floorDirection(degrees: Float): Pair<Float, Float> {
            val a = radians(degrees)
            return sin(a) to -cos(a)
        }

        /** A flat ring on the floor between two radii, as a triangle strip. */
        fun ringStrip(inner: Float, outer: Float): FloatArray {
            val out = FloatArray((RING_SEGMENTS + 1) * 2 * 3)
            var i = 0
            for (segment in 0..RING_SEGMENTS) {
                val (x, z) = floorDirection(segment * 360f / RING_SEGMENTS)
                for (radius in floatArrayOf(inner, outer)) {
                    out[i++] = x * radius
                    out[i++] = 0f
                    out[i++] = z * radius
                }
            }
            return out
        }

        /** A thin unit ring cut into [DASHES] dashes, two triangles each. */
        fun dashedRing(): FloatArray {
            val out = FloatArray(DASHES * 2 * 3 * 3)
            val inner = 1f - THIN_RING_WIDTH
            var i = 0
            for (dash in 0 until DASHES) {
                val (x0, z0) = floorDirection(dash * 360f / DASHES)
                val (x1, z1) = floorDirection((dash + DASH_DUTY) * 360f / DASHES)
                val corners = floatArrayOf(
                    x0 * inner, z0 * inner, x0, z0, x1, z1,
                    x0 * inner, z0 * inner, x1, z1, x1 * inner, z1 * inner,
                )
                for (corner in 0 until 6) {
                    out[i++] = corners[corner * 2]
                    out[i++] = 0f
                    out[i++] = corners[corner * 2 + 1]
                }
            }
            return out
        }

        /** One triangle per degree from −45° to +45°, so any symmetric part of it can be drawn. */
        fun sectorTriangles(): FloatArray {
            val out = FloatArray(SECTOR_MAX_HALF * 2 * 3 * 3)
            var i = 0
            for (degree in -SECTOR_MAX_HALF until SECTOR_MAX_HALF) {
                val (x0, z0) = floorDirection(degree.toFloat())
                val (x1, z1) = floorDirection(degree + 1f)
                val corners = floatArrayOf(0f, 0f, x0, z0, x1, z1)
                for (corner in 0 until 3) {
                    out[i++] = corners[corner * 2] * SECTOR_RADIUS
                    out[i++] = 0f
                    out[i++] = corners[corner * 2 + 1] * SECTOR_RADIUS
                }
            }
            return out
        }

        const val FLAT_VERTEX = """
            uniform mat4 uMvp;
            uniform float uRadial;
            attribute vec3 aPos;
            varying float vFade;
            void main() {
                vFade = uRadial > 0.0 ? length(aPos.xz) * uRadial : 1.0;
                gl_Position = uMvp * vec4(aPos, 1.0);
            }
        """

        const val FLAT_FRAGMENT = """
            precision mediump float;
            uniform vec4 uColor;
            uniform vec2 uViewport;
            uniform vec2 uFade;
            uniform vec2 uTop;
            uniform float uEdge;
            varying float vFade;
            void main() {
                float fade = vFade;
                if (uEdge > 0.0) {
                    vec2 p = gl_FragCoord.xy / uViewport;
                    float edge = min(min(p.x, 1.0 - p.x), min(p.y, 1.0 - p.y));
                    fade *= smoothstep(0.0, uEdge, edge) * smoothstep(uFade.x, uFade.y, gl_FragCoord.y);
                    fade *= 1.0 - smoothstep(uTop.x, uTop.y, gl_FragCoord.y);
                }
                gl_FragColor = uColor * fade;
            }
        """

        const val ARROW_VERTEX = """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            uniform vec3 uInvScale;
            attribute vec3 aPos;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            varying vec3 vWorld;
            void main() {
                vWorld = (uModel * vec4(aPos, 1.0)).xyz;
                vNormal = (uModel * vec4(aNormal * uInvScale, 0.0)).xyz;
                gl_Position = uMvp * vec4(aPos, 1.0);
            }
        """

        // Matte two-tone (design #76): the top face is the plain colour (#FFFFFF), the sides its cool
        // shade (#C9D6F5 for white), bevels in between. Only a hint of highlight and rim.
        const val ARROW_FRAGMENT = """
            precision mediump float;
            uniform vec3 uBase;
            uniform vec3 uLight;
            uniform vec3 uCamera;
            uniform float uGloss;
            varying vec3 vNormal;
            varying vec3 vWorld;
            void main() {
                vec3 n = normalize(vNormal);
                vec3 v = normalize(uCamera - vWorld);
                float diffuse = max(dot(n, uLight), 0.0);
                vec3 shade = vec3(0.79, 0.84, 0.96);
                vec3 color = uBase * mix(shade, vec3(1.0), smoothstep(0.56, 0.80, diffuse));
                float highlight = pow(max(dot(n, normalize(uLight + v)), 0.0), 48.0) * uGloss;
                float rim = pow(1.0 - max(dot(n, v), 0.0), 3.0);
                color += vec3(highlight) + vec3(0.40, 0.58, 1.0) * rim * 0.12;
                gl_FragColor = vec4(min(color, vec3(1.0)), 1.0);
            }
        """
    }
}
