package com.gamenuri.btscanner24.core.ui.map3d

/**
 * Where each device stood on screen in the last drawn frame, so a tap can be matched to one. Written
 * whole by the GL thread and read by the main thread; never changed after it is made.
 */
internal class MapHits(private val keys: List<String>, private val spots: FloatArray) {

    /**
     * The device at ([x], [y]) in view pixels, or null when the tap is on none. Where two overlap the
     * one whose middle is closer, measured in its own size, wins: the nearer, larger one in a tie.
     */
    fun keyAt(x: Float, y: Float): String? {
        var best: String? = null
        var bestShare = 1f
        for (index in keys.indices) {
            val radius = spots[index * FLOATS + 2]
            if (radius <= 0f) continue
            val dx = x - spots[index * FLOATS]
            val dy = y - spots[index * FLOATS + 1]
            val share = (dx * dx + dy * dy) / (radius * radius)
            if (share <= bestShare) {
                bestShare = share
                best = keys[index]
            }
        }
        return best
    }

    companion object {
        /** Per device: x, y (from the top) and the radius that counts as a hit, in pixels. */
        const val FLOATS = 3
        val NONE = MapHits(emptyList(), FloatArray(0))
    }
}
