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
 * perspective camera that follows how the phone is held. OpenGL ES 2.0 only, so every test phone
 * (the Android 11 A50 too) runs the same shaders.
 *
 * The scene is heading-relative: −Z is where the phone points, +X its right, +Y up. All easing
 * happens here on the GL thread, so the UI thread only sets targets in [SceneState].
 */
internal class SceneRenderer(private val mesh: FloatArray, private val state: SceneState) {

    private var arrowProgram = 0
    private var flatProgram = 0
    private var meshBuffer = 0
    private var ringBuffer = 0
    private var sectorBuffer = 0
    private val meshVertices = mesh.size / ObjMesh.FLOATS_PER_VERTEX

    private val projection = FloatArray(16)
    private val lookAt = FloatArray(16)
    private val rollMatrix = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val model = FloatArray(16)
    private val shadowModel = FloatArray(16)
    private val mvp = FloatArray(16)
    private val shadow = FloatArray(16)
    private var aspect = 1f

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

    fun onSurfaceCreated() {
        arrowProgram = program(ARROW_VERTEX, ARROW_FRAGMENT)
        flatProgram = program(FLAT_VERTEX, FLAT_FRAGMENT)
        val buffers = IntArray(3)
        GLES20.glGenBuffers(3, buffers, 0)
        meshBuffer = buffers[0]
        ringBuffer = buffers[1]
        sectorBuffer = buffers[2]
        upload(meshBuffer, mesh)
        upload(ringBuffer, ringStrip())
        upload(sectorBuffer, sectorTriangles())
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        // Premultiplied alpha: the layer under this view (the find screen's blue) shows through.
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = if (height > 0) width.toFloat() / height else 1f
    }

    fun draw(deltaSeconds: Float) {
        val yaw = advance(deltaSeconds.coerceIn(0f, MAX_STEP_SECONDS))
        camera()
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_STENCIL_BUFFER_BIT)

        val bob = if (state.reduceMotion) 0f else HOVER_BOB * sin(time * TAU / HOVER_PERIOD_SECONDS)
        val hover = HOVER_HEIGHT + bob
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, 0f, hover, 0f)
        Matrix.rotateM(model, 0, -yaw, 0f, 1f, 0f)
        Matrix.scaleM(model, 0, width * MESH_SCALE, MESH_SCALE, MESH_SCALE)

        drawFloor(yaw)
        drawShadow()
        drawArrow()
    }

    /** Moves every eased value one step and returns the arrow's yaw (clockwise from "ahead"). */
    private fun advance(dt: Float): Float {
        time += dt
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
        Matrix.perspectiveM(projection, 0, FIELD_OF_VIEW, aspect, NEAR, FAR)
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

    private fun drawFloor(yaw: Float) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glUseProgram(flatProgram)
        val position = GLES20.glGetAttribLocation(flatProgram, "aPos")
        val color = state.floorColor

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ringBuffer)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, 0)
        val ringVertices = (RING_SEGMENTS + 1) * 2
        flat(viewProjection, color, RING_ALPHA)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, ringVertices)
        // A second, fainter ring closer in gives the floor some depth.
        Matrix.scaleM(mvp, 0, viewProjection, 0, INNER_RING, 1f, INNER_RING)
        flat(mvp, color, INNER_RING_ALPHA)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, ringVertices)
        if (state.spinning) {
            // Sweep progress, clockwise from straight ahead.
            val segments = (state.progress.coerceIn(0f, 1f) * RING_SEGMENTS).roundToInt()
            if (segments > 0) {
                flat(viewProjection, color, 1f)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, (segments + 1) * 2)
            }
        } else {
            // "Somewhere this way": the sector turns with the arrow and widens as confidence drops.
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sectorBuffer)
            GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, 0)
            Matrix.rotateM(mvp, 0, viewProjection, 0, -yaw, 0f, 1f, 0f)
            flat(mvp, color, SECTOR_ALPHA, radial = true)
            val half = sectorHalf.roundToInt().coerceIn(1, SECTOR_MAX_HALF)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, (SECTOR_MAX_HALF - half) * 3, half * 2 * 3)
        }
        GLES20.glDisableVertexAttribArray(position)
    }

    /**
     * The mesh dropped straight onto the floor, nudged towards the viewer. Three faint copies of
     * growing size stand in for a blur; the stencil keeps each copy from doubling up on itself.
     */
    private fun drawShadow() {
        GLES20.glUseProgram(flatProgram)
        val position = GLES20.glGetAttribLocation(flatProgram, "aPos")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, meshBuffer)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 0)
        GLES20.glEnable(GLES20.GL_STENCIL_TEST)
        GLES20.glStencilOp(GLES20.GL_KEEP, GLES20.GL_KEEP, GLES20.GL_INCR)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(flatProgram, "uRadial"), 0f)
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
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, meshVertices)
        }
        GLES20.glDisable(GLES20.GL_STENCIL_TEST)
        GLES20.glDisableVertexAttribArray(position)
    }

    private fun drawArrow() {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(arrowProgram)
        val position = GLES20.glGetAttribLocation(arrowProgram, "aPos")
        val normal = GLES20.glGetAttribLocation(arrowProgram, "aNormal")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, meshBuffer)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glEnableVertexAttribArray(normal)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 0)
        GLES20.glVertexAttribPointer(normal, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 3 * BYTES_PER_FLOAT)

        Matrix.multiplyMM(mvp, 0, viewProjection, 0, model, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(arrowProgram, "uMvp"), 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(arrowProgram, "uModel"), 1, false, model, 0)
        // Normals of a mesh stretched sideways: divide by the stretch twice (inverse transpose).
        GLES20.glUniform3f(GLES20.glGetUniformLocation(arrowProgram, "uInvScale"), 1f / (width * width), 1f, 1f)
        val color = state.arrowColor
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
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, meshVertices)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glDisableVertexAttribArray(normal)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun flat(matrix: FloatArray, color: Int, alpha: Float, radial: Boolean = false) {
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(flatProgram, "uMvp"), 1, false, matrix, 0)
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
        const val FIELD_OF_VIEW = 30f
        const val NEAR = 0.5f
        const val FAR = 30f
        const val CAMERA_DISTANCE = 6.6f
        const val TARGET_HEIGHT = 0.12f
        const val MAX_ROLL = 30f
        const val POSE_SMOOTH_SECONDS = 0.09f

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
        const val RING_OUTER = 1.36f
        const val RING_INNER = RING_OUTER - 0.026f
        const val INNER_RING = 0.62f
        const val RING_ALPHA = 0.35f
        const val INNER_RING_ALPHA = 0.18f
        const val SECTOR_RADIUS = RING_INNER
        const val SECTOR_MAX_HALF = 45
        const val SECTOR_LOCKED = 12f
        const val SECTOR_NARROW = 20f
        const val SECTOR_WIDE = 35f
        const val SECTOR_ALPHA = 0.22f

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

        fun ringStrip(): FloatArray {
            val out = FloatArray((RING_SEGMENTS + 1) * 2 * 3)
            var i = 0
            for (segment in 0..RING_SEGMENTS) {
                val (x, z) = floorDirection(segment * 360f / RING_SEGMENTS)
                for (radius in floatArrayOf(RING_INNER, RING_OUTER)) {
                    out[i++] = x * radius
                    out[i++] = 0f
                    out[i++] = z * radius
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
            varying float vFade;
            void main() {
                gl_FragColor = uColor * vFade;
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
