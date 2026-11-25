package com.gamenuri.btscanner24.core.ui.map3d

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.gamenuri.btscanner24.core.ui.arrow3d.DeviceModel
import com.gamenuri.btscanner24.core.ui.arrow3d.GlLayerView
import com.gamenuri.btscanner24.core.ui.arrow3d.GlScene
import java.io.IOException
import kotlin.math.abs
import kotlin.math.ceil

/**
 * The device map (#86) on its own GL layer: reads the models the pins need, draws their name tags
 * into bitmaps for the renderer and turns a tap into the device under it.
 */
internal class MapSceneView(context: Context) : GlLayerView(context) {

    val state = MapSceneState()

    /** The key of the tapped device, or null for the empty floor. */
    var onTap: ((String?) -> Unit)? = null

    private val wanted = HashSet<DeviceModel>()
    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var stepTexts: List<String> = emptyList()
    private var downX = 0f
    private var downY = 0f
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = LABEL_TEXT_SP * density * resources.configuration.fontScale.coerceAtMost(MAX_FONT_SCALE)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = FLOOR_TEXT_PX
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = FLOOR_TEXT
    }

    init {
        loadMe()
    }

    fun setPins(pins: List<MapPin>) {
        if (pins == state.pins) return
        for (pin in pins) load(pin.model)
        state.labels = labelsFor(pins, state.labels)
        state.pins = pins
    }

    fun setStepLabels(texts: List<String>) {
        if (texts == stepTexts) return
        stepTexts = texts
        state.stepLabels = texts.map { floorText(it) }
    }

    fun setPose(headingDegrees: Float, elevationDegrees: Float, @Suppress("UNUSED_PARAMETER") rollDegrees: Float) {
        state.headingDegrees = headingDegrees
        state.elevationDegrees = elevationDegrees
        state.hasPose = true
    }

    override fun createScene(): GlScene = MapRenderer(state)

    @SuppressLint("ClickableViewAccessibility") // The list under the map offers every device to TalkBack.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }
            MotionEvent.ACTION_UP -> {
                val still = abs(event.x - downX) <= touchSlop && abs(event.y - downY) <= touchSlop
                if (still) onTap?.invoke(state.hits.keyAt(event.x, event.y))
            }
        }
        return true
    }

    /** Each model's file is read once, on a worker thread; the renderer picks it up when it is there. */
    private fun load(model: DeviceModel) {
        if (!wanted.add(model)) return
        DeviceModel.cached(model)?.let {
            state.meshes[model] = it
            return
        }
        val assets = context.applicationContext
        Thread {
            DeviceModel.load(assets, model)?.let { state.meshes[model] = it }
        }.start()
    }

    private fun loadMe() {
        val assets = context.applicationContext.assets
        Thread {
            state.meMesh = try {
                assets.open(ME_ASSET).use { DeviceModel.parse(it.readBytes()) }
            } catch (_: IOException) {
                null
            }
        }.start()
    }

    /** Tags for [pins]: those already drawn are kept, so a list that only moved costs nothing. */
    private fun labelsFor(pins: List<MapPin>, drawn: Map<String, Bitmap>): Map<String, Bitmap> {
        val labels = HashMap<String, Bitmap>()
        for (pin in pins) {
            val text = pin.label
            if (text.isEmpty() || text in labels) continue
            labels[text] = drawn[text] ?: tag(text)
        }
        return labels
    }

    /** A white pill with the text in the map's dark blue (design bt-map-3d v2). */
    private fun tag(text: String): Bitmap {
        val shown = if (text.length > MAX_LABEL_CHARS) text.take(MAX_LABEL_CHARS - 1) + "…" else text
        val padX = LABEL_PAD_X_DP * density
        val padY = LABEL_PAD_Y_DP * density
        val metrics = textPaint.fontMetrics
        val width = ceil(textPaint.measureText(shown) + 2 * padX).toInt().coerceAtLeast(1)
        val height = ceil(metrics.descent - metrics.ascent + 2 * padY).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        pillPaint.color = LABEL_PILL
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), height / 2f, height / 2f, pillPaint)
        textPaint.color = LABEL_TEXT
        canvas.drawText(shown, padX, padY - metrics.ascent, textPaint)
        return bitmap
    }

    /** White text with nothing behind it, to lie on a step; a fixed size, as it is scaled in the scene. */
    private fun floorText(text: String): Bitmap {
        val metrics = floorPaint.fontMetrics
        val width = ceil(floorPaint.measureText(text)).toInt().coerceAtLeast(1)
        val height = ceil(metrics.descent - metrics.ascent).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, 0f, -metrics.ascent, floorPaint)
        return bitmap
    }

    private companion object {
        const val ME_ASSET = "models/nearo_me_chevron.mesh"
        const val LABEL_TEXT_SP = 14f

        // A very large system font would make the tags cover the devices they name.
        const val MAX_FONT_SCALE = 1.3f
        const val LABEL_PAD_X_DP = 10f
        const val LABEL_PAD_Y_DP = 4f
        const val MAX_LABEL_CHARS = 26
        const val LABEL_PILL = 0xFFFFFFFF.toInt()
        const val LABEL_TEXT = 0xFF1B3FA8.toInt()
        const val FLOOR_TEXT_PX = 64f
        const val FLOOR_TEXT = 0xEBFFFFFF.toInt()
    }
}
