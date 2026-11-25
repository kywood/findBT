package com.gamenuri.btscanner24.core.ui.arrow3d

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.util.Log
import android.view.TextureView
import android.view.View

/** What a [GlLayerView] draws; every call comes from the view's own GL thread. */
internal interface GlScene {
    fun onSurfaceCreated()

    fun onSurfaceChanged(width: Int, height: Int)

    fun draw(deltaSeconds: Float)
}

/**
 * A see-through [TextureView] with its own OpenGL ES 2.0 thread, shared by the 3D find scene (#76)
 * and the device map (#86). A TextureView (not a SurfaceView) so it blends with the Compose screen
 * around it, clips with the water transition, and can later sit on top of a camera preview.
 *
 * The thread draws only while the view is on screen; when GL cannot start, [onUnavailable] is
 * called once on the main thread so the caller can fall back to a flat drawing.
 */
internal abstract class GlLayerView(context: Context) :
    TextureView(context),
    TextureView.SurfaceTextureListener {

    var onUnavailable: (() -> Unit)? = null

    private var thread: RenderThread? = null

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    /** The scene for a new surface; called on the GL thread with its context current. */
    protected abstract fun createScene(): GlScene

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        thread = RenderThread(surface, width, height).also {
            it.paused = !isShown
            it.start()
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        thread?.resize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        thread?.finish()
        thread = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        thread?.paused = !isShown
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        thread?.paused = visibility != VISIBLE
    }

    private inner class RenderThread(
        private val surface: SurfaceTexture,
        @Volatile private var width: Int,
        @Volatile private var height: Int,
    ) : Thread("nearo-scene") {
        @Volatile var paused = false

        @Volatile private var running = true

        @Volatile private var resized = true

        fun resize(newWidth: Int, newHeight: Int) {
            width = newWidth
            height = newHeight
            resized = true
        }

        /** Stops drawing and waits until the surface is no longer used. */
        fun finish() {
            running = false
            interrupt()
            try {
                join(JOIN_TIMEOUT_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        override fun run() {
            var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
            var context: EGLContext = EGL14.EGL_NO_CONTEXT
            var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
            try {
                display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                val version = IntArray(2)
                check(display != EGL14.EGL_NO_DISPLAY && EGL14.eglInitialize(display, version, 0, version, 1)) {
                    "EGL display"
                }
                // Smooth edges where the phone offers 4× multisampling; plain otherwise.
                val config = chooseConfig(display, samples = 4) ?: chooseConfig(display, samples = 0)
                checkNotNull(config) { "EGL config" }
                context = EGL14.eglCreateContext(
                    display,
                    config,
                    EGL14.EGL_NO_CONTEXT,
                    intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                    0,
                )
                check(context != EGL14.EGL_NO_CONTEXT) { "EGL context" }
                eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
                check(eglSurface != EGL14.EGL_NO_SURFACE) { "EGL surface" }
                check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "EGL current" }

                val renderer = createScene()
                renderer.onSurfaceCreated()
                var last = System.nanoTime()
                while (running) {
                    if (paused) {
                        Thread.sleep(PAUSED_POLL_MS)
                        last = System.nanoTime()
                        continue
                    }
                    if (resized) {
                        resized = false
                        renderer.onSurfaceChanged(width, height)
                    }
                    val now = System.nanoTime()
                    renderer.draw((now - last) / NANOS_PER_SECOND)
                    last = now
                    if (!EGL14.eglSwapBuffers(display, eglSurface)) break
                    // A TextureView does not block on vsync by itself; hold the loop to the display rate.
                    val spent = (System.nanoTime() - now) / NANOS_PER_MILLI
                    if (spent < FRAME_MS) Thread.sleep(FRAME_MS - spent)
                }
            } catch (_: InterruptedException) {
                // finish() was called.
            } catch (e: RuntimeException) {
                // No usable GL here (or a shader this driver rejects): the caller draws its flat fallback.
                // Logged so that is never a silent surprise; the message holds no scan data.
                Log.w("NearoScene", "3D scene unavailable, falling back to the flat drawing", e)
                if (running) post { onUnavailable?.invoke() }
            } finally {
                if (display != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
                    if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                    // The display is shared with the app's own renderer, so it is not terminated.
                    EGL14.eglReleaseThread()
                }
            }
        }

        private fun chooseConfig(display: EGLDisplay, samples: Int): EGLConfig? {
            val attributes = mutableListOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 16,
                EGL14.EGL_STENCIL_SIZE, 8,
            )
            if (samples > 0) attributes += listOf(EGL14.EGL_SAMPLE_BUFFERS, 1, EGL14.EGL_SAMPLES, samples)
            attributes += EGL14.EGL_NONE
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            val found = EGL14.eglChooseConfig(display, attributes.toIntArray(), 0, configs, 0, 1, count, 0)
            return if (found && count[0] > 0) configs[0] else null
        }
    }

    private companion object {
        const val FRAME_MS = 16L
        const val PAUSED_POLL_MS = 100L
        const val JOIN_TIMEOUT_MS = 500L
        const val NANOS_PER_SECOND = 1_000_000_000f
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
