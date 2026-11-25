package com.gamenuri.btscanner24.core.ui.map3d

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import com.gamenuri.btscanner24.core.ui.arrow3d.BallMesh
import com.gamenuri.btscanner24.core.ui.arrow3d.DeviceModel
import com.gamenuri.btscanner24.core.ui.arrow3d.DiscMesh
import com.gamenuri.btscanner24.core.ui.arrow3d.GlScene
import com.gamenuri.btscanner24.core.ui.arrow3d.ObjMesh
import com.gamenuri.btscanner24.core.ui.arrow3d.RimMesh
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the device map as a real 3D space (design bt-map-3d v2, #86): a stepped floor of three
 * round slabs with light rims, "me" hovering in the middle as a white chevron (#123), and every
 * device as its model on a white stand with a soft shadow and a name tag, fading into the screen's
 * blue far out. A device whose direction was really measured gets a white beam from the middle
 * (v2.1). OpenGL ES 2.0 only, like the find scene, and nothing but flat or vertex colours.
 *
 * The scene is heading-relative: −Z is where the phone points, +X its right, +Y up; the camera
 * always looks from +Z. Devices with a measured direction turn round the middle as the phone turns;
 * all others keep their place on screen, as their place on the ring says nothing about a direction. All easing happens
 * here on the GL thread, so the UI thread only sets targets in [MapSceneState].
 */
internal class MapRenderer(private val state: MapSceneState) : GlScene {
    private val slabs =
        Array(MapFloor.STEPS) { DiscMesh.build(MapFloor.STEP_RADII[it], MapFloor.STEP_HEIGHT, SLAB_BEVEL) }
    private val rims = Array(MapFloor.STEPS) { RimMesh.build(MapFloor.STEP_RADII[it], RIM_WIDTH, RIM_HEIGHT) }
    private val stand = DiscMesh.build(STAND_RADIUS, STAND_THICKNESS, STAND_BEVEL)
    private val standRing = RimMesh.build(STAND_RING, STAND_RING_WIDTH, STAND_RING_HEIGHT)
    private val meRing = RimMesh.build(ME_RING, ME_RING_WIDTH, ME_RING_HEIGHT)
    private val dome = BallMesh.build(DOME_RADIUS)
    private val measuredRing = RimMesh.build(MEASURED_RING, MEASURED_RING_WIDTH, MEASURED_RING_HEIGHT)
    private val box = MapMeshes.box()
    private val arrowhead = MapMeshes.arrowhead()

    private var litProgram = 0
    private var flatProgram = 0
    private var tagProgram = 0
    private var decalProgram = 0
    private val slabBuffers = IntArray(MapFloor.STEPS)
    private val rimBuffers = IntArray(MapFloor.STEPS)
    private val contactBuffers = IntArray(MapFloor.STEPS)
    private var standBuffer = 0
    private var standRingBuffer = 0
    private var meRingBuffer = 0
    private var domeBuffer = 0
    private var discBuffer = 0
    private var thinBuffer = 0
    private var sectorBuffer = 0
    private var quadBuffer = 0
    private var pulseBuffer = 0
    private var measuredRingBuffer = 0
    private var boxBuffer = 0
    private var arrowheadBuffer = 0
    private var meBuffer = 0
    private var meVertices = 0
    private var uploadedMe: FloatArray? = null

    private class Model(val buffer: Int, val vertices: Int, val height: Float)

    private class Tag(val texture: Int, val width: Int, val height: Int)

    /** A device's eased place; GL thread only. */
    private class Spot(var angle: Float, var radius: Float) {
        var shown = 0f
        var picked = 0f
        var seen = true

        // Where its name tag hangs, set when it is drawn.
        var tagX = 0f
        var tagHeight = 0f
        var tagMiddle = 0f
        var tagZ = 0f

        // Where it is drawn this frame: [angle] and [radius], moved to stay below the chips (#193).
        var drawAngle = 0f
        var drawRadius = 0f
    }

    private val models = HashMap<DeviceModel, Model>()
    private val tags = HashMap<String, Tag>()
    private var uploadedLabels: Map<String, Bitmap>? = null
    private var stepTags: List<Tag> = emptyList()
    private var uploadedSteps: List<Bitmap>? = null
    private val spots = HashMap<String, Spot>()
    private var order = IntArray(0)
    private var ranks = FloatArray(0)
    private var rects = FloatArray(0)
    private var hitSpots = FloatArray(0)

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val probe = FloatArray(4)
    private val projected = FloatArray(4)

    /** [fitInView]'s answer: degrees, radius. */
    private val fitted = FloatArray(2)

    /** The "me" marker on screen (left, bottom, right, top in pixels from the lower edge): no tag lies on it. */
    private val meRect = FloatArray(RECT_FLOATS)
    private var viewportWidth = 1f
    private var viewportHeight = 1f

    // Eased values.
    private var initialized = false
    private var time = 0f
    private var heading = 0f
    private var elevation = MapCamera.TYPICAL

    /** 0 = direction unknown (the dome), 1 = known (the chevron). */
    private var known = 0f

    override fun onSurfaceCreated() {
        litProgram = program(LIT_VERTEX, LIT_FRAGMENT, "aPos", "aNormal", "aColor")
        flatProgram = program(FLAT_VERTEX, FLAT_FRAGMENT, "aPos")
        tagProgram = program(TAG_VERTEX, TAG_FRAGMENT, "aCorner")
        decalProgram = program(DECAL_VERTEX, TAG_FRAGMENT, "aCorner")
        // A new context: nothing uploaded before is there any more.
        models.clear()
        tags.clear()
        uploadedLabels = null
        stepTags = emptyList()
        uploadedSteps = null
        uploadedMe = null
        meVertices = 0
        val buffers = IntArray(FIXED_BUFFERS)
        GLES20.glGenBuffers(FIXED_BUFFERS, buffers, 0)
        var next = 0
        for (step in 0 until MapFloor.STEPS) {
            slabBuffers[step] = buffers[next++]
            rimBuffers[step] = buffers[next++]
            contactBuffers[step] = buffers[next++]
            val edge = MapFloor.STEP_RADII[step]
            upload(contactBuffers[step], ringStrip(edge, edge + CONTACT_WIDTH))
            upload(slabBuffers[step], slabs[step])
            upload(rimBuffers[step], rims[step])
        }
        standBuffer = buffers[next++]
        standRingBuffer = buffers[next++]
        meRingBuffer = buffers[next++]
        domeBuffer = buffers[next++]
        discBuffer = buffers[next++]
        thinBuffer = buffers[next++]
        sectorBuffer = buffers[next++]
        quadBuffer = buffers[next++]
        pulseBuffer = buffers[next++]
        measuredRingBuffer = buffers[next++]
        boxBuffer = buffers[next++]
        arrowheadBuffer = buffers[next++]
        meBuffer = buffers[next]
        upload(pulseBuffer, ringStrip(1f - PULSE_RING_WIDTH, 1f))
        upload(measuredRingBuffer, measuredRing)
        upload(boxBuffer, box)
        upload(arrowheadBuffer, arrowhead)
        upload(standBuffer, stand)
        upload(standRingBuffer, standRing)
        upload(meRingBuffer, meRing)
        upload(domeBuffer, dome)
        upload(discBuffer, ringStrip(0f, 1f))
        upload(thinBuffer, ringStrip(1f - THIN_RING_WIDTH, 1f))
        upload(sectorBuffer, sectorFan())
        upload(quadBuffer, floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        // Premultiplied alpha: the screen's blue under this view shows through.
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth = width.coerceAtLeast(1).toFloat()
        viewportHeight = height.coerceAtLeast(1).toFloat()
    }

    override fun draw(deltaSeconds: Float) {
        val dt = deltaSeconds.coerceIn(0f, MAX_STEP_SECONDS)
        syncMeshes()
        syncTags()
        syncStepLabels()
        val pins = state.pins
        advance(dt, pins)
        camera()
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glDepthMask(true)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        // Nothing of the scene, its name tags included, lies over the bars above or the sheet below.
        val top = state.clipTop.coerceIn(0f, 1f)
        val bottom = state.clipBottom.coerceIn(top, 1f)
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        GLES20.glScissor(
            0,
            ((1f - bottom) * viewportHeight).toInt(),
            viewportWidth.toInt(),
            ((bottom - top) * viewportHeight).toInt(),
        )

        fitPins(pins)
        drawFloor()
        drawFloorMarks(pins.isEmpty())
        drawStepLabels()
        drawShadows(pins)
        drawMe()
        drawPins(pins)
        drawTags(pins)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
    }

    /** Moves every eased value one step. */
    private fun advance(dt: Float, pins: List<MapPin>) {
        time += dt
        val still = state.reduceMotion
        val targetElevation =
            if (state.hasPose && !still) MapCamera.elevationFor(state.elevationDegrees) else MapCamera.TYPICAL
        if (!initialized) {
            initialized = true
            heading = state.headingDegrees
            elevation = targetElevation
            known = if (state.hasPose) 1f else 0f
        }
        val fast = 1f - exp(-dt / POSE_SMOOTH_SECONDS)
        val slow = 1f - exp(-dt / MOVE_SMOOTH_SECONDS)
        heading += wrap(state.headingDegrees - heading) * fast
        elevation += (targetElevation - elevation) * fast
        val targetKnown = if (state.hasPose) 1f else 0f
        known = if (still) targetKnown else known + (targetKnown - known) * (1f - exp(-dt / KNOWN_SMOOTH_SECONDS))

        for (spot in spots.values) spot.seen = false
        val selected = state.selectedKey
        for (pin in pins) {
            val radius = MapFloor.radius(pin.ring)
            val spot = spots.getOrPut(pin.key) { Spot(pin.angleDegrees, radius) }
            spot.seen = true
            val targetPicked = if (pin.key == selected) 1f else 0f
            if (still) {
                spot.angle = pin.angleDegrees
                spot.radius = radius
                spot.shown = 1f
                spot.picked = targetPicked
            } else {
                spot.angle += wrap(pin.angleDegrees - spot.angle) * slow
                spot.radius += (radius - spot.radius) * slow
                spot.shown += (1f - spot.shown) * slow
                spot.picked += (targetPicked - spot.picked) * slow
            }
        }
        if (spots.size > pins.size) spots.values.removeAll { !it.seen }
    }

    /**
     * The design's camera: 16.5 units from a point a little beyond the middle, 42° tall on a 6:13
     * screen. The width it shows is kept on every screen shape (a taller view only sees more floor
     * and sky), and the floor's middle sits at [MapSceneState.centerY] of the height.
     */
    private fun camera() {
        val halfWidth = NEAR * HALF_WIDTH_PER_DISTANCE
        val tall = 2f * halfWidth * viewportHeight / viewportWidth
        val above = state.centerY.coerceIn(0f, 1f)
        Matrix.frustumM(projection, 0, -halfWidth, halfWidth, -tall * (1f - above), tall * above, NEAR, FAR)
        val e = radians(elevation)
        Matrix.setLookAtM(
            view, 0,
            0f, EYE_LIFT + CAMERA_DISTANCE * sin(e), CAMERA_DISTANCE * cos(e),
            0f, TARGET_HEIGHT, TARGET_AHEAD,
            0f, 1f, 0f,
        )
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
    }

    /** How far measured devices are turned round the middle: with the phone, or not at all in a still view. */
    private fun worldTurn(): Float = if (state.hasPose && !state.reduceMotion) heading else 0f

    /** The three slabs and their rims, tinted from the screen's blue: lighter towards the middle. */
    private fun drawFloor() {
        val background = state.backgroundColor
        for (step in MapFloor.STEPS - 1 downTo 0) {
            val top = -step * MapFloor.STEP_HEIGHT
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, 0f, top - MapFloor.STEP_HEIGHT / 2, 0f)
            val tint = lighten(background, SLAB_LIGHT[step])
            drawLit(slabBuffers[step], slabs[step].size / ObjMesh.FLOATS_PER_VERTEX, false, tint)
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, 0f, top, 0f)
            drawLit(rimBuffers[step], rims[step].size / ObjMesh.FLOATS_PER_VERTEX, false, RIM_COLOR)
        }
    }

    /**
     * What lies flat on the floor: two faint rings far out for depth, and, once the heading is known,
     * the sector the phone looks along. Blended, tested against the solids, never written to the depth.
     */
    private fun drawFloorMarks(empty: Boolean) {
        beginFlat()
        val position = GLES20.glGetAttribLocation(flatProgram, "aPos")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, thinBuffer)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, 0)
        val ground = -MapFloor.STEPS * MapFloor.STEP_HEIGHT
        for (step in 0 until MapFloor.STEPS) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, contactBuffers[step])
            GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, 0)
            flatAt(0f, -(step + 1) * MapFloor.STEP_HEIGHT + CONTACT_LIFT, 0f, 1f, SHADOW_COLOR, CONTACT_ALPHA)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, RING_VERTICES)
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, thinBuffer)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, 0)
        for (index in FAR_RINGS.indices) {
            flatAt(0f, ground, 0f, FAR_RINGS[index], WHITE, FAR_RING_ALPHAS[index])
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, RING_VERTICES)
        }
        if (known > EPSILON) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sectorBuffer)
            GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, 0)
            val turn = meTurn()
            for (index in SECTOR_RADII.indices) {
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, 0f, SECTOR_LIFT * (index + 1), 0f)
                Matrix.rotateM(model, 0, -turn, 0f, 1f, 0f)
                Matrix.scaleM(model, 0, SECTOR_RADII[index], 1f, SECTOR_RADII[index])
                flat(WHITE, SECTOR_ALPHAS[index] * known)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, SECTOR_SEGMENTS + 2)
            }
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, pulseBuffer)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, 0)
        if (known < 1f - EPSILON) {
            // Direction unknown (#123 D): rings spreading slowly from the dome.
            val spread = if (state.reduceMotion) 0f else (time / PULSE_PERIOD_SECONDS) % 1f
            for (index in PULSE_RADII.indices) {
                val radius = PULSE_RADII[index] * (1f + PULSE_GROWTH * spread)
                val alpha = PULSE_ALPHAS[index] * (1f - PULSE_FADE * spread) * (1f - known)
                flatAt(0f, SECTOR_LIFT, 0f, radius, PULSE_COLOR, alpha)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, RING_VERTICES)
            }
        }
        if (empty) {
            // Nothing nearby yet: three rings run out over the whole floor, each on the step under it.
            for (ring in 0 until SCAN_RINGS) {
                val phase = if (state.reduceMotion) 0f else time / SCAN_PERIOD_SECONDS
                val spread = (phase + ring.toFloat() / SCAN_RINGS) % 1f
                val radius = SCAN_FROM + (SCAN_TO - SCAN_FROM) * spread
                flatAt(0f, MapFloor.height(radius) + SCAN_LIFT, 0f, radius, WHITE, SCAN_ALPHA * (1f - spread))
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, RING_VERTICES)
            }
        }
        GLES20.glDisableVertexAttribArray(position)
        endFlat()
    }

    /** "1 m", "5 m", "10 m+" lying on their steps at the front right, where no device hides them for long. */
    private fun drawStepLabels() {
        val labels = stepTags
        if (labels.size < MapFloor.STEPS) return
        beginFlat()
        GLES20.glUseProgram(decalProgram)
        val corner = GLES20.glGetAttribLocation(decalProgram, "aCorner")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadBuffer)
        GLES20.glEnableVertexAttribArray(corner)
        GLES20.glVertexAttribPointer(corner, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(decalProgram, "uTex"), 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(decalProgram, "uAlpha"), 1f)
        val (x, z) = floorDirection(STEP_LABEL_DEGREES)
        for (step in 0 until MapFloor.STEPS) {
            val tag = labels[step]
            val radius = MapFloor.STEP_RADII[step] - STEP_LABEL_INSET
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, x * radius, -step * MapFloor.STEP_HEIGHT + STEP_LABEL_LIFT, z * radius)
            Matrix.rotateM(model, 0, STEP_LABEL_TURN_DEGREES, 0f, 1f, 0f)
            Matrix.scaleM(model, 0, STEP_LABEL_HEIGHT * tag.width / tag.height, 1f, STEP_LABEL_HEIGHT)
            Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(decalProgram, "uMvp"), 1, false, mvp, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tag.texture)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glDisableVertexAttribArray(corner)
        endFlat()
    }

    /** A soft round shadow beside every stand, away from the light, and one under the chevron. */
    private fun drawShadows(pins: List<MapPin>) {
        beginFlat()
        val position = GLES20.glGetAttribLocation(flatProgram, "aPos")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, discBuffer)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, 0)
        for (pin in pins) {
            val spot = spots[pin.key] ?: continue
            val (x, z) = floorDirection(spot.drawAngle)
            val floor = MapFloor.height(spot.drawRadius) + SHADOW_LIFT
            val px = x * spot.drawRadius + SHADOW_ASIDE
            val pz = z * spot.drawRadius + SHADOW_TOWARDS
            for (index in SHADOW_SCALES.indices) {
                flatAt(px, floor, pz, SHADOW_RADIUS * SHADOW_SCALES[index] * pinSize(spot), SHADOW_COLOR, SHADOW_ALPHA)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, RING_VERTICES)
            }
        }
        if (known > EPSILON && meVertices > 0) {
            // The chevron dropped straight onto the floor (its height scaled away), nudged like the others.
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, meBuffer)
            GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, MODEL_STRIDE, 0)
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, SHADOW_ASIDE * ME_SHADOW_SHARE, SHADOW_LIFT, SHADOW_TOWARDS * ME_SHADOW_SHARE)
            Matrix.rotateM(model, 0, -meTurn(), 0f, 1f, 0f)
            Matrix.scaleM(model, 0, ME_SIZE * known, 0f, ME_SIZE * known)
            flat(SHADOW_COLOR, ME_SHADOW_ALPHA)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, meVertices)
        }
        GLES20.glDisableVertexAttribArray(position)
        endFlat()
    }

    /** Which way the chevron points: ahead while the view turns with the phone, the heading when it stands still. */
    private fun meTurn(): Float = if (state.reduceMotion && state.hasPose) heading else 0f

    /** "Me" (#123): the white chevron hovering over a thin ring, or a pale dome while the heading is unknown. */
    private fun drawMe() {
        if (known > EPSILON) {
            Matrix.setIdentityM(model, 0)
            drawLit(meRingBuffer, meRing.size / ObjMesh.FLOATS_PER_VERTEX, false, WHITE, alpha = known)
            if (meVertices > 0) {
                val bob = if (state.reduceMotion) 0f else ME_BOB * sin(time * ME_BOB_SPEED)
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, 0f, ME_HOVER + bob, 0f)
                Matrix.rotateM(model, 0, -meTurn(), 0f, 1f, 0f)
                Matrix.scaleM(model, 0, ME_SIZE * known, ME_SIZE * known, ME_SIZE * known)
                drawLit(meBuffer, meVertices, true, WHITE)
            }
        }
        if (known < 1f - EPSILON) {
            val size = 1f - known
            Matrix.setIdentityM(model, 0)
            Matrix.scaleM(model, 0, size, size, size)
            drawLit(domeBuffer, dome.size / ObjMesh.FLOATS_PER_VERTEX, false, DOME_COLOR)
        }
    }

    /** A device's size: the design's 78 %, a little smaller on the floor beyond the steps, growing in when new. */
    /** Works out where every device is drawn this frame; see [fitInView]. */
    private fun fitPins(pins: List<MapPin>) {
        val turn = worldTurn()
        for (pin in pins) {
            val spot = spots[pin.key] ?: continue
            fitInView(spot.angle - if (pin.measured) turn else 0f, spot.radius, pinSize(spot), pin.measured)
            spot.drawAngle = fitted[0]
            spot.drawRadius = fitted[1]
        }
    }

    /**
     * Keeps a whole device below the chips (#193): a model cut in half at the chip line reads as a
     * glitch. A device without a measured direction has no real place on its ring, so it is first
     * pulled in across its band and, if even the band's inner edge is too high, moved round the ring
     * towards the side until it fits. A measured device keeps its direction and is only pulled in.
     * The result, degrees and radius, is left in [fitted].
     */
    private fun fitInView(degrees: Float, radius: Float, size: Float, measured: Boolean) {
        fitted[0] = degrees
        fitted[1] = radius
        val limit = (1f - state.clipTop.coerceIn(0f, 1f)) * viewportHeight - PIN_EDGE_MARGIN
        if (pinTop(degrees, radius, size) <= limit) return
        val band = MapFloor.band(radius)
        val inner = minOf(radius, if (band == 0) 0f else MapFloor.STEP_RADII[band - 1] + PIN_BAND_INSET)
        if (pinTop(degrees, inner, size) <= limit) {
            var low = inner
            var high = radius
            repeat(FIT_STEPS) {
                val mid = (low + high) / 2f
                if (pinTop(degrees, mid, size) <= limit) low = mid else high = mid
            }
            fitted[1] = low
            return
        }
        fitted[1] = inner
        if (measured) return
        // Straight ahead is the top of the screen; turning towards the nearer side brings it down.
        val side = if (wrap(degrees) >= 0f) 1f else -1f
        var low = 0f
        var high = MAX_FIT_TURN
        if (pinTop(degrees + side * high, inner, size) > limit) {
            fitted[0] = degrees + side * high
            return
        }
        repeat(FIT_STEPS) {
            val mid = (low + high) / 2f
            if (pinTop(degrees + side * mid, inner, size) <= limit) high = mid else low = mid
        }
        fitted[0] = degrees + side * high
    }

    /** The top of a device standing at [degrees], [radius] on screen, pixels from the lower edge. */
    private fun pinTop(degrees: Float, radius: Float, size: Float): Float {
        val (dx, dz) = floorDirection(degrees)
        project(dx * radius, MapFloor.height(radius) + PIN_TOP * size, dz * radius)
        val w = projected[3]
        if (w <= 0f) return Float.MAX_VALUE
        return (projected[1] / w + 1f) / 2f * viewportHeight
    }

    private fun pinSize(spot: Spot): Float {
        val far = if (MapFloor.band(spot.radius) >= MapFloor.STEPS) FAR_PIN_SHARE else 1f
        return PIN_SIZE * far * spot.shown
    }

    /** Every device: its stand, the ring on it (mint when picked) and its model. */
    private fun drawPins(pins: List<MapPin>) {
        if (hitSpots.size != pins.size * MapHits.FLOATS) hitSpots = FloatArray(pins.size * MapHits.FLOATS)
        for ((index, pin) in pins.withIndex()) {
            val spot = spots[pin.key]
            if (spot == null) {
                hitSpots[index * MapHits.FLOATS + 2] = 0f
                continue
            }
            drawPin(pin, spot, index)
        }
        state.hits = MapHits(pins.map { it.key }, hitSpots.copyOf())
    }

    /**
     * "Direction measured" (v2.1): a white beam on the floor from the "me" ring towards the device,
     * ending in an arrowhead short of its stand, and a second white ring round the stand.
     */
    private fun drawMeasured(spot: Spot, degrees: Float, x: Float, z: Float, floor: Float, size: Float) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, floor, z)
        Matrix.scaleM(model, 0, size, size, size)
        drawLit(measuredRingBuffer, measuredRing.size / ObjMesh.FLOATS_PER_VERTEX, false, WHITE)
        val end = spot.drawRadius - BEAM_STAND_GAP * size - BEAM_HEAD_ROOM
        val length = end - BEAM_FROM
        if (length < BEAM_MIN_LENGTH) return
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, -degrees, 0f, 1f, 0f)
        Matrix.translateM(model, 0, 0f, BEAM_HEIGHT, -(BEAM_FROM + length / 2))
        Matrix.scaleM(model, 0, BEAM_WIDTH, BEAM_THICKNESS, length)
        drawLit(boxBuffer, box.size / ObjMesh.FLOATS_PER_VERTEX, false, WHITE)
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, -degrees, 0f, 1f, 0f)
        Matrix.translateM(model, 0, 0f, BEAM_HEIGHT, -(end + BEAM_HEAD_PAST))
        Matrix.scaleM(model, 0, BEAM_HEAD, BEAM_THICKNESS, BEAM_HEAD * BEAM_HEAD_STRETCH)
        drawLit(arrowheadBuffer, arrowhead.size / ObjMesh.FLOATS_PER_VERTEX, false, WHITE)
    }

    private fun drawPin(pin: MapPin, spot: Spot, index: Int) {
        // Only a measured direction is fixed in the room; a tidy place on the ring stays where it is on
        // screen, so no device has to be looked for in a direction nobody knows (#86).
        val size = pinSize(spot)
        val degrees = spot.drawAngle
        val radius = spot.drawRadius
        val (dx, dz) = floorDirection(degrees)
        val x = dx * radius
        val z = dz * radius
        val floor = MapFloor.height(radius)
        if (pin.measured) drawMeasured(spot, degrees, x, z, floor, size)

        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, floor + STAND_THICKNESS / 2 * size, z)
        Matrix.scaleM(model, 0, size, size, size)
        drawLit(standBuffer, stand.size / ObjMesh.FLOATS_PER_VERTEX, false, STAND_COLOR)

        val ringSize = size * (1f + PICKED_RING_GROWTH * spot.picked)
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, floor + STAND_THICKNESS * size, z)
        Matrix.scaleM(model, 0, ringSize, size, ringSize)
        val ringColor = mix(STAND_RING_COLOR, state.accentColor, spot.picked)
        drawLit(standRingBuffer, standRing.size / ObjMesh.FLOATS_PER_VERTEX, false, ringColor)

        val mesh = models[pin.model]
        var top = floor + (STAND_THICKNESS + MODEL_GAP) * size
        if (mesh != null) {
            val bob = if (state.reduceMotion) 0f else PICKED_BOB * sin(time * PICKED_BOB_SPEED)
            val base = top + (PICKED_LIFT + bob) * spot.picked
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, x, base, z)
            Matrix.rotateM(model, 0, MODEL_TURN_DEGREES, 0f, 1f, 0f)
            Matrix.scaleM(model, 0, MODEL_SIZE * size, MODEL_SIZE * size, MODEL_SIZE * size)
            drawLit(mesh.buffer, mesh.vertices, true, WHITE)
            top = base + mesh.height * MODEL_SIZE * size
        }

        // Where it is on screen: its middle for taps, and the place of its name tag over it.
        val middle = (floor + top) / 2
        project(x, middle, z)
        val w = projected[3]
        val offset = index * MapHits.FLOATS
        if (w <= 0f) {
            hitSpots[offset + 2] = 0f
            return
        }
        hitSpots[offset] = (projected[0] / w + 1f) / 2f * viewportWidth
        hitSpots[offset + 1] = (1f - projected[1] / w) / 2f * viewportHeight
        // Pixels per scene unit at this depth; never smaller than a fingertip.
        val pixelsPerUnit = viewportWidth / (2f * HALF_WIDTH_PER_DISTANCE * w)
        hitSpots[offset + 2] = maxOf(HIT_RADIUS * pixelsPerUnit, MIN_HIT_SHARE * viewportWidth) * spot.shown
        spot.tagHeight = top + TAG_GAP
        spot.tagMiddle = middle
        spot.tagX = x
        spot.tagZ = z
    }

    /**
     * The name tags, upright and sharp: one screen pixel per bitmap pixel whatever the distance. Where
     * two would overlap only one is shown (design v2.2): the picked device's first, then those with a
     * measured direction, then the nearer ring. A tag pushed mostly off the side is not shown either,
     * nor one that would reach under the chips or the sheet (#193). A tag never covers "me" (#194).
     */
    private fun drawTags(pins: List<MapPin>) {
        if (pins.isEmpty() || tags.isEmpty()) return
        if (order.size != pins.size) {
            order = IntArray(pins.size)
            ranks = FloatArray(pins.size)
            rects = FloatArray(pins.size * RECT_FLOATS)
        }
        val selected = state.selectedKey
        for (index in pins.indices) {
            order[index] = index
            val pin = pins[index]
            val spot = spots[pin.key]
            ranks[index] = when {
                spot == null -> Float.MAX_VALUE
                pin.key == selected -> RANK_PICKED
                pin.measured -> RANK_MEASURED
                else -> spot.radius
            }
        }
        // The few there are, most important first.
        for (i in 1 until order.size) {
            val moved = order[i]
            var j = i - 1
            while (j >= 0 && ranks[order[j]] > ranks[moved]) {
                order[j + 1] = order[j]
                j--
            }
            order[j + 1] = moved
        }
        measureMe()
        var kept = 0
        for (index in order) {
            if (placeTag(pins[index], kept)) {
                order[kept] = index
                kept++
            }
        }
        if (kept == 0) return

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glUseProgram(tagProgram)
        val corner = GLES20.glGetAttribLocation(tagProgram, "aCorner")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadBuffer)
        GLES20.glEnableVertexAttribArray(corner)
        GLES20.glVertexAttribPointer(corner, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(tagProgram, "uTex"), 0)
        for (place in 0 until kept) {
            val pin = pins[order[place]]
            val tag = tags[pin.label] ?: continue
            val spot = spots[pin.key] ?: continue
            val at = place * RECT_FLOATS
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tag.texture)
            GLES20.glUniform4f(
                GLES20.glGetUniformLocation(tagProgram, "uRect"),
                rects[at] / viewportWidth * 2f - 1f,
                rects[at + 1] / viewportHeight * 2f - 1f,
                tag.width / viewportWidth * 2f,
                tag.height / viewportHeight * 2f,
            )
            GLES20.glUniform1f(GLES20.glGetUniformLocation(tagProgram, "uAlpha"), spot.shown)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glDisableVertexAttribArray(corner)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    /**
     * Finds the place of [pin]'s tag in whole pixels (left, bottom from the view's lower edge, right,
     * top) and writes it as rectangle number [kept]. Over the device first; where that lies on another
     * tag, on "me" or past the visible band, beside the device, right then left. False when it has no
     * tag, is out of sight, or no place is free.
     */
    private fun placeTag(pin: MapPin, kept: Int): Boolean {
        val tag = tags[pin.label] ?: return false
        val spot = spots[pin.key] ?: return false
        project(spot.tagX, spot.tagHeight, spot.tagZ)
        val w = projected[3]
        if (w <= 0f) return false
        val anchor = (projected[0] / w + 1f) / 2f * viewportWidth
        // Mostly past the side of the screen: not shown, rather than a tag far from its device.
        val past = maxOf(-anchor, anchor - viewportWidth)
        if (past > TAG_OFF_SCREEN_SHARE * tag.width) return false
        val aboveBottom = (projected[1] / w + 1f) / 2f * viewportHeight
        project(spot.tagX, spot.tagMiddle, spot.tagZ)
        val middleW = projected[3]
        if (middleW <= 0f) return false
        val middle = (projected[1] / middleW + 1f) / 2f * viewportHeight
        val beside = TAG_SIDE_GAP * viewportWidth / (2f * HALF_WIDTH_PER_DISTANCE * middleW)
        val sideBottom = middle - tag.height / 2f
        return tryTag(tag, kept, anchor - tag.width / 2f, aboveBottom) ||
            tryTag(tag, kept, anchor + beside, sideBottom) ||
            tryTag(tag, kept, anchor - beside - tag.width, sideBottom)
    }

    /** Writes rectangle number [kept] at [left], [bottom] if it fits the visible band and lies on nothing. */
    private fun tryTag(tag: Tag, kept: Int, left: Float, bottom: Float): Boolean {
        val edge = maxOf(0f, minOf(TAG_EDGE_MARGIN, (viewportWidth - tag.width) / 2f))
        val l = Math.round(left.coerceIn(edge, maxOf(edge, viewportWidth - tag.width - edge))).toFloat()
        val b = Math.round(bottom).toFloat()
        val r = l + tag.width
        val t = b + tag.height
        // The band between the chips above and the sheet below, as the scissor test cuts it.
        val lowest = (1f - state.clipBottom.coerceIn(0f, 1f)) * viewportHeight + TAG_PADDING
        val highest = (1f - state.clipTop.coerceIn(0f, 1f)) * viewportHeight - TAG_PADDING
        if (b < lowest || t > highest) return false
        if (overlaps(l, b, r, t, meRect, 0)) return false
        for (other in 0 until kept) {
            if (overlaps(l, b, r, t, rects, other * RECT_FLOATS)) return false
        }
        val at = kept * RECT_FLOATS
        rects[at] = l
        rects[at + 1] = b
        rects[at + 2] = r
        rects[at + 3] = t
        return true
    }

    private fun overlaps(left: Float, bottom: Float, right: Float, top: Float, other: FloatArray, at: Int): Boolean =
        !(
            left - TAG_PADDING >= other[at + 2] ||
                right + TAG_PADDING <= other[at] ||
                bottom - TAG_PADDING >= other[at + 3] ||
                top + TAG_PADDING <= other[at + 1]
            )

    /** Where "me" stands on screen this frame, from the floor to the top of the chevron; empty when behind. */
    private fun measureMe() {
        meRect.fill(0f)
        project(0f, 0f, 0f)
        val w = projected[3]
        if (w <= 0f) return
        val x = (projected[0] / w + 1f) / 2f * viewportWidth
        val bottom = (projected[1] / w + 1f) / 2f * viewportHeight
        project(0f, ME_HOVER + ME_SIZE, 0f)
        val topW = projected[3]
        if (topW <= 0f) return
        val top = (projected[1] / topW + 1f) / 2f * viewportHeight
        val half = ME_RING * viewportWidth / (2f * HALF_WIDTH_PER_DISTANCE * w)
        meRect[0] = x - half
        meRect[1] = bottom
        meRect[2] = x + half
        meRect[3] = maxOf(top, bottom)
    }

    private fun project(x: Float, y: Float, z: Float) {
        probe[0] = x
        probe[1] = y
        probe[2] = z
        probe[3] = 1f
        Matrix.multiplyMV(projected, 0, viewProjection, 0, probe, 0)
    }

    /** Hands newly read models to GL; nothing new costs nothing. */
    private fun syncMeshes() {
        val me = state.meMesh
        if (me !== uploadedMe) {
            uploadedMe = me
            meVertices = 0
            if (me != null && me.isNotEmpty()) {
                upload(meBuffer, me)
                meVertices = me.size / DeviceModel.FLOATS_PER_VERTEX
            }
        }
        if (models.size == state.meshes.size) return
        for ((kind, mesh) in state.meshes) {
            if (kind in models || mesh.isEmpty()) continue
            val buffer = IntArray(1)
            GLES20.glGenBuffers(1, buffer, 0)
            upload(buffer[0], mesh)
            var height = 0f
            for (vertex in 1 until mesh.size step DeviceModel.FLOATS_PER_VERTEX) height = maxOf(height, mesh[vertex])
            models[kind] = Model(buffer[0], mesh.size / DeviceModel.FLOATS_PER_VERTEX, height)
        }
    }

    private fun syncStepLabels() {
        val wanted = state.stepLabels
        if (wanted === uploadedSteps) return
        uploadedSteps = wanted
        for (tag in stepTags) GLES20.glDeleteTextures(1, intArrayOf(tag.texture), 0)
        stepTags = wanted.map { texture(it) }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    /** A bitmap as a texture, sampled smoothly and never repeated. Leaves it bound. */
    private fun texture(bitmap: Bitmap): Tag {
        val texture = IntArray(1)
        GLES20.glGenTextures(1, texture, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return Tag(texture[0], bitmap.width, bitmap.height)
    }

    /** Name tags as textures: new texts are uploaded, texts no longer shown are freed. */
    private fun syncTags() {
        val labels = state.labels
        if (labels === uploadedLabels) return
        uploadedLabels = labels
        val gone = tags.keys.filter { it !in labels }
        for (text in gone) {
            val tag = tags.remove(text) ?: continue
            GLES20.glDeleteTextures(1, intArrayOf(tag.texture), 0)
        }
        for ((text, bitmap) in labels) {
            if (text in tags) continue
            tags[text] = texture(bitmap)
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    /**
     * A lit mesh at [model]. [coloured]: it carries its own vertex colours (a baked model, nine floats
     * per vertex); otherwise it is one colour.
     */
    private fun drawLit(buffer: Int, vertices: Int, coloured: Boolean, color: Int, alpha: Float = 1f) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(true)
        if (alpha < 1f) GLES20.glEnable(GLES20.GL_BLEND) else GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(litProgram)
        val stride = if (coloured) MODEL_STRIDE else PLAIN_STRIDE
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer)
        GLES20.glEnableVertexAttribArray(POSITION)
        GLES20.glEnableVertexAttribArray(NORMAL)
        GLES20.glVertexAttribPointer(POSITION, 3, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glVertexAttribPointer(NORMAL, 3, GLES20.GL_FLOAT, false, stride, 3 * BYTES_PER_FLOAT)
        if (coloured) {
            GLES20.glEnableVertexAttribArray(COLOR)
            GLES20.glVertexAttribPointer(COLOR, 3, GLES20.GL_FLOAT, false, stride, 6 * BYTES_PER_FLOAT)
        } else {
            // No colours in the mesh: every vertex gets white, tinted below.
            GLES20.glDisableVertexAttribArray(COLOR)
            GLES20.glVertexAttrib3f(COLOR, 1f, 1f, 1f)
        }
        Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(litProgram, "uMvp"), 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(litProgram, "uModel"), 1, false, model, 0)
        GLES20.glUniform3f(GLES20.glGetUniformLocation(litProgram, "uLight"), LIGHT[0], LIGHT[1], LIGHT[2])
        val tint = GLES20.glGetUniformLocation(litProgram, "uTint")
        GLES20.glUniform4f(tint, red(color), green(color), blue(color), alpha)
        val fog = state.backgroundColor
        GLES20.glUniform3f(GLES20.glGetUniformLocation(litProgram, "uFog"), red(fog), green(fog), blue(fog))
        GLES20.glUniform2f(GLES20.glGetUniformLocation(litProgram, "uFogRange"), FOG_FROM, FOG_TO - FOG_FROM)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertices)
        GLES20.glDisableVertexAttribArray(POSITION)
        GLES20.glDisableVertexAttribArray(NORMAL)
        if (coloured) GLES20.glDisableVertexAttribArray(COLOR)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    /** Flat, blended drawing on the floor: hidden behind solids, but leaving the depth as it is. */
    private fun beginFlat() {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glEnable(GLES20.GL_BLEND)
        // Pulled a little towards the viewer, so a mark never flickers against the floor under it.
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glPolygonOffset(-2f, -2f)
        GLES20.glUseProgram(flatProgram)
    }

    private fun endFlat() {
        GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glDepthMask(true)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    /** A unit shape of the flat program lying at ([x], [y], [z]), scaled to [radius]. */
    private fun flatAt(x: Float, y: Float, z: Float, radius: Float, color: Int, alpha: Float) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, y, z)
        Matrix.scaleM(model, 0, radius, 1f, radius)
        flat(color, alpha)
    }

    /** Sets the flat program's matrix (from [model]) and its colour, premultiplied. */
    private fun flat(color: Int, alpha: Float) {
        Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(flatProgram, "uMvp"), 1, false, mvp, 0)
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

    /** Links a program with its attributes at fixed places, in the order given (the position first, at 0). */
    private fun program(vertex: String, fragment: String, vararg attributes: String): Int {
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, shader(GLES20.GL_VERTEX_SHADER, vertex))
        GLES20.glAttachShader(program, shader(GLES20.GL_FRAGMENT_SHADER, fragment))
        for ((index, name) in attributes.withIndex()) GLES20.glBindAttribLocation(program, index, name)
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
        const val PLAIN_STRIDE = ObjMesh.FLOATS_PER_VERTEX * BYTES_PER_FLOAT
        const val MODEL_STRIDE = DeviceModel.FLOATS_PER_VERTEX * BYTES_PER_FLOAT
        const val MAX_STEP_SECONDS = 0.05f
        const val EPSILON = 0.01f
        const val FIXED_BUFFERS = MapFloor.STEPS * 3 + 13

        // Attribute places of the lit program, as bound in [program].
        const val POSITION = 0
        const val NORMAL = 1
        const val COLOR = 2

        // Camera (the designer's scene values, map3d-v2-reference): 42° tall on a 540 × 1170 screen is
        // tan(21°) × 540 / 1170 = 0.177 of the distance to each side.
        const val HALF_WIDTH_PER_DISTANCE = 0.1772f

        // Close planes: the 16-bit depth of the layer has to tell a mark from the step it lies on.
        const val NEAR = 4f
        const val FAR = 60f
        const val CAMERA_DISTANCE = 16.5f
        const val EYE_LIFT = 0.4f
        const val TARGET_HEIGHT = 0.5f
        const val TARGET_AHEAD = -1.2f
        const val POSE_SMOOTH_SECONDS = 0.12f
        const val MOVE_SMOOTH_SECONDS = 0.35f
        const val KNOWN_SMOOTH_SECONDS = 0.1f

        // The scene fades into the screen's blue between these distances from the camera.
        const val FOG_FROM = 13f
        const val FOG_TO = 30f

        // Floor (v2.2): slabs lightened from the screen's blue (#5590FF, #3C78FF and #2B67FF on #2563FF,
        // from the middle outwards), rims almost white, and on the next step down a band of the shadow
        // blue just outside each slab's edge, where it meets the floor.
        val SLAB_LIGHT = floatArrayOf(0.25f, 0.12f, 0.03f)
        const val SLAB_BEVEL = 0.012f
        const val RIM_WIDTH = 0.1f
        const val RIM_HEIGHT = 0.05f
        const val RIM_COLOR = 0xFFF2F6FF.toInt()
        const val CONTACT_WIDTH = 0.34f
        const val CONTACT_ALPHA = 0.30f
        const val WHITE = 0xFFFFFFFF.toInt()
        const val RING_SEGMENTS = 96
        const val RING_VERTICES = (RING_SEGMENTS + 1) * 2
        const val THIN_RING_WIDTH = 0.006f
        val FAR_RINGS = floatArrayOf(9.5f, 13f)
        val FAR_RING_ALPHAS = floatArrayOf(0.10f, 0.06f)

        // "Me" (#123, lengths in scene units; r1 = 1.7): the chevron 0.85 long hovering at 0.42 over a
        // ring of 0.66; a sector 41° wide out to the second step. Unknown heading: a dome of 0.27 and
        // three rings at 0.48, 0.82 and 1.16 that spread a little and fade, every 2 s.
        const val ME_SIZE = 0.85f
        const val ME_HOVER = 0.34f
        const val ME_BOB = 0.03f
        const val ME_BOB_SPEED = 2.2f
        const val ME_RING = 0.66f
        const val ME_RING_WIDTH = 0.05f
        const val ME_RING_HEIGHT = 0.03f
        const val ME_SHADOW_ALPHA = 0.28f
        const val ME_SHADOW_SHARE = 0.5f
        const val SECTOR_SEGMENTS = 16
        const val SECTOR_HALF_DEGREES = 20.6f
        val SECTOR_RADII = floatArrayOf(MapFloor.STEP_RADII[1], MapFloor.STEP_RADII[0])
        val SECTOR_ALPHAS = floatArrayOf(0.12f, 0.14f)
        const val SECTOR_LIFT = 0.012f
        const val DOME_RADIUS = 0.27f
        const val DOME_COLOR = 0xFFDCE6FF.toInt()
        val PULSE_RADII = floatArrayOf(0.48f, 0.82f, 1.16f)
        val PULSE_ALPHAS = floatArrayOf(0.75f, 0.40f, 0.20f)
        const val PULSE_COLOR = 0xFFBFD0FF.toInt()
        const val PULSE_PERIOD_SECONDS = 2f
        const val PULSE_GROWTH = 0.4f
        const val PULSE_FADE = 0.8f

        // Devices (design v2 at 78 % size, #131): a white stand of radius 0.62 and height 0.16 with a
        // blue ring of 0.66 on it, the model (longest side 1) just over it, turned a little to the viewer.
        const val PIN_SIZE = 0.78f
        const val STAND_RADIUS = 0.62f
        const val STAND_THICKNESS = 0.16f
        const val STAND_BEVEL = 0.03f
        const val STAND_COLOR = 0xFFF3F5FA.toInt()
        const val STAND_RING = 0.7f
        const val STAND_RING_WIDTH = 0.08f
        const val STAND_RING_HEIGHT = 0.03f
        const val STAND_RING_COLOR = 0xFF3F7BFF.toInt()
        const val MODEL_SIZE = 1.05f
        const val MODEL_GAP = 0.03f
        const val MODEL_TURN_DEGREES = -20f
        const val FAR_PIN_SHARE = 0.9f

        // "Direction measured" (v2.1): a beam 0.22 wide and 0.05 thick, 0.06 over the middle step, from
        // the "me" ring (0.82) to short of the stand; an arrowhead of 0.36, stretched 1.25 along it,
        // 0.10 past its end; a white ring of 0.80 round the stand.
        const val BEAM_FROM = 0.82f
        const val BEAM_WIDTH = 0.22f
        const val BEAM_THICKNESS = 0.05f
        const val BEAM_HEIGHT = 0.06f
        const val BEAM_STAND_GAP = 0.7f
        const val BEAM_HEAD_ROOM = 0.34f
        const val BEAM_HEAD = 0.36f
        const val BEAM_HEAD_STRETCH = 1.25f
        const val BEAM_HEAD_PAST = 0.10f
        const val BEAM_MIN_LENGTH = 0.2f
        const val MEASURED_RING = 0.83f
        const val MEASURED_RING_WIDTH = 0.06f
        const val MEASURED_RING_HEIGHT = 0.04f

        // Nothing nearby: three rings from 0.9 to 6.7, 70 % fading to nothing, every 2.9 s.
        const val SCAN_RINGS = 3
        const val SCAN_PERIOD_SECONDS = 2.9f
        const val SCAN_FROM = 0.9f
        const val SCAN_TO = 6.7f
        const val SCAN_ALPHA = 0.7f
        const val SCAN_LIFT = 0.04f
        const val PULSE_RING_WIDTH = 0.012f

        // The distances on the steps: 0.5 tall, 0.45 inside each rim, at the front right.
        const val STEP_LABEL_DEGREES = 146f
        const val STEP_LABEL_INSET = 0.45f
        const val STEP_LABEL_HEIGHT = 0.5f
        const val STEP_LABEL_LIFT = 0.012f
        const val STEP_LABEL_TURN_DEGREES = 20f

        // Picked: the ring turns mint and grows by 12 %, the model lifts 0.22 and bobs ± 0.05.
        const val PICKED_RING_GROWTH = 0.12f
        const val PICKED_LIFT = 0.22f
        const val PICKED_BOB = 0.05f
        const val PICKED_BOB_SPEED = 5f

        // Shadows: #0B2A8A, never black; two discs of growing size stand in for a blur.
        const val SHADOW_COLOR = 0xFF0B2A8A.toInt()
        const val SHADOW_ALPHA = 0.13f
        const val SHADOW_RADIUS = 0.62f
        val SHADOW_SCALES = floatArrayOf(1.0f, 1.18f)
        const val SHADOW_ASIDE = 0.2f
        const val SHADOW_TOWARDS = 0.12f
        const val SHADOW_LIFT = 0.006f
        const val CONTACT_LIFT = 0.003f

        // Name tags and taps.
        const val TAG_GAP = 0.34f
        const val RECT_FLOATS = 4
        const val RANK_PICKED = -2f
        const val RANK_MEASURED = -1f
        const val TAG_PADDING = 4f
        const val TAG_EDGE_MARGIN = 8f
        const val TAG_OFF_SCREEN_SHARE = 0.6f

        /** From a device's middle to the near edge of a tag set beside it, in scene units (#194). */
        const val TAG_SIDE_GAP = 0.75f

        // Keeping whole devices below the chips (#193).
        /** A device's height over its floor, stand and model, as a share of its size. */
        const val PIN_TOP = 1.4f
        const val PIN_EDGE_MARGIN = 6f
        const val PIN_BAND_INSET = 0.5f
        const val FIT_STEPS = 8
        const val MAX_FIT_TURN = 90f
        const val HIT_RADIUS = 0.7f
        const val MIN_HIT_SHARE = 0.06f

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

        /** [from] turned [share] of the way to [to], channel by channel. */
        fun mix(from: Int, to: Int, share: Float): Int {
            val s = share.coerceIn(0f, 1f)
            fun channel(shift: Int): Int {
                val a = from shr shift and 0xFF
                val b = to shr shift and 0xFF
                return (a + (b - a) * s).toInt() and 0xFF
            }
            return 0xFF000000.toInt() or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }

        fun lighten(color: Int, share: Float): Int = mix(color, WHITE, share)

        /** Direction on the floor at [degrees] clockwise from straight ahead (−Z). */
        fun floorDirection(degrees: Float): Pair<Float, Float> {
            val a = radians(degrees)
            return sin(a) to -cos(a)
        }

        /** A flat ring on the floor between two radii, as a triangle strip. */
        fun ringStrip(inner: Float, outer: Float): FloatArray {
            val out = FloatArray(RING_VERTICES * 3)
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

        /** The sector ahead as a fan of radius 1: the middle, then the arc from left to right. */
        fun sectorFan(): FloatArray {
            val out = FloatArray((SECTOR_SEGMENTS + 2) * 3)
            var i = 3
            for (segment in 0..SECTOR_SEGMENTS) {
                val degrees = -SECTOR_HALF_DEGREES + 2f * SECTOR_HALF_DEGREES * segment / SECTOR_SEGMENTS
                val (x, z) = floorDirection(degrees)
                out[i++] = x
                out[i++] = 0f
                out[i++] = z
            }
            return out
        }

        const val FLAT_VERTEX = """
            uniform mat4 uMvp;
            attribute vec3 aPos;
            void main() {
                gl_Position = uMvp * vec4(aPos, 1.0);
            }
        """

        const val FLAT_FRAGMENT = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                gl_FragColor = uColor;
            }
        """

        const val LIT_VERTEX = """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            uniform vec2 uFogRange;
            attribute vec3 aPos;
            attribute vec3 aNormal;
            attribute vec3 aColor;
            varying vec3 vNormal;
            varying vec3 vColor;
            varying float vFog;
            void main() {
                vNormal = (uModel * vec4(aNormal, 0.0)).xyz;
                vColor = aColor;
                gl_Position = uMvp * vec4(aPos, 1.0);
                vFog = clamp((gl_Position.w - uFogRange.x) / uFogRange.y, 0.0, 1.0);
            }
        """

        // The find scene's matte two-tone, on the mesh's own colours, fading into the screen's blue.
        const val LIT_FRAGMENT = """
            precision mediump float;
            uniform vec3 uLight;
            uniform vec4 uTint;
            uniform vec3 uFog;
            varying vec3 vNormal;
            varying vec3 vColor;
            varying float vFog;
            void main() {
                float diffuse = max(dot(normalize(vNormal), uLight), 0.0);
                vec3 shade = vec3(0.72, 0.78, 0.92);
                vec3 base = vColor * uTint.rgb;
                vec3 color = base * mix(shade, vec3(1.0), smoothstep(0.30, 0.85, diffuse));
                color = mix(color, uFog, vFog);
                gl_FragColor = vec4(color * uTint.a, uTint.a);
            }
        """

        const val TAG_VERTEX = """
            uniform vec4 uRect;
            attribute vec2 aCorner;
            varying vec2 vUv;
            void main() {
                vUv = vec2(aCorner.x, 1.0 - aCorner.y);
                gl_Position = vec4(uRect.xy + aCorner * uRect.zw, 0.0, 1.0);
            }
        """

        // A texture lying on the floor: the quad's far edge is the picture's top.
        const val DECAL_VERTEX = """
            uniform mat4 uMvp;
            attribute vec2 aCorner;
            varying vec2 vUv;
            void main() {
                vUv = vec2(aCorner.x, 1.0 - aCorner.y);
                gl_Position = uMvp * vec4(aCorner.x - 0.5, 0.0, 0.5 - aCorner.y, 1.0);
            }
        """

        const val TAG_FRAGMENT = """
            precision mediump float;
            uniform sampler2D uTex;
            uniform float uAlpha;
            varying vec2 vUv;
            void main() {
                gl_FragColor = texture2D(uTex, vUv) * uAlpha;
            }
        """
    }
}
