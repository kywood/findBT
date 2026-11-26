package com.gamenuri.btscanner24.core.ui.arrow3d

import android.content.Context
import android.view.ViewTreeObserver

/** The 3D find scene (#76) on its own GL layer: holds its [SceneState] and reads the device model for it. */
internal class SceneTextureView(context: Context) : GlLayerView(context) {

    val state = SceneState()

    private val mesh: FloatArray by lazy { ArrowMesh.build() }
    private var model: DeviceModel? = null

    /** Which device model the scene shows; its file is read on a worker thread and then handed to the renderer. */
    fun setModel(next: DeviceModel?) {
        if (next == model) return
        model = next
        if (next == null) {
            state.modelMesh = null
            return
        }
        // Read before (the find screen showed it): there at once, e.g. for its exit on "Found it".
        DeviceModel.cached(next)?.let {
            state.modelMesh = it
            return
        }
        val assets = context.applicationContext
        Thread {
            val mesh = DeviceModel.load(assets, next)
            // Only if this is still the model that is wanted.
            post { if (model == next) state.modelMesh = mesh }
        }.start()
    }

    private val location = IntArray(2)
    private val fadeUpdater = ViewTreeObserver.OnPreDrawListener {
        updateFade()
        true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(fadeUpdater)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(fadeUpdater)
        super.onDetachedFromWindow()
    }

    /** Design find-flow-3d v1.1: the rings are whole down to half the screen and gone from 60 %. */
    private fun updateFade() {
        val screen = rootView.height
        if (screen <= 0 || height <= 0) return
        getLocationInWindow(location)
        val bottom = location[1] + height
        state.fadeFullY = bottom - screen * FADE_FULL_AT
        state.fadeClearY = bottom - screen * FADE_CLEAR_AT
    }

    fun setPose(headingDegrees: Float, elevationDegrees: Float, rollDegrees: Float) {
        state.headingDegrees = headingDegrees
        state.elevationDegrees = elevationDegrees
        state.rollDegrees = rollDegrees
        state.hasPose = true
    }

    override fun createScene(): GlScene = SceneRenderer(mesh, state)

    private companion object {
        const val FADE_FULL_AT = 0.5f
        const val FADE_CLEAR_AT = 0.6f
    }
}
