package com.gamenuri.btscanner24.core.model

/**
 * Keeps each device on its map ring while its distance wobbles across a band's edge (#193). A device
 * only moves to another band once its distance has been clearly inside that band, [EDGE_MARGIN]
 * past the edge, for [DWELL_MS]; until then its distance is held just inside the band it is on.
 * A device seen for the first time takes its band at once.
 */
class MapBandHold {
    private class Held(var band: Int, var wanted: Int = -1, var since: Long = 0L, var seen: Long = 0L)

    private val held = HashMap<String, Held>()

    /** The [entries] with each distance kept inside the band its device is held on. */
    fun hold(entries: List<MapEntry>, now: Long): List<MapEntry> {
        val out = entries.map { entry -> entry.copy(distanceMeters = hold(entry.key, entry.distanceMeters, now)) }
        held.values.removeAll { now - it.seen > FORGET_MS }
        return out
    }

    private fun hold(key: String, meters: Double?, now: Long): Double? {
        val band = MapLayout.ring(meters).toInt()
        val state = held.getOrPut(key) { Held(band) }
        state.seen = now
        if (band == state.band) {
            state.wanted = -1
            return meters
        }
        if (!clearlyIn(band, state.band, meters)) {
            state.wanted = -1
            return inside(state.band, meters)
        }
        if (state.wanted != band) {
            state.wanted = band
            state.since = now
        }
        if (now - state.since < DWELL_MS) return inside(state.band, meters)
        state.band = band
        state.wanted = -1
        return meters
    }

    /** Whether [meters] lies past the edge between [from] and [to] by [EDGE_MARGIN]. */
    private fun clearlyIn(to: Int, from: Int, meters: Double?): Boolean {
        if (meters == null) return true
        return if (to > from) meters >= EDGES[from] * EDGE_MARGIN else meters * EDGE_MARGIN <= EDGES[to]
    }

    /** [meters] moved just inside [band], so the ring it gives stays on that band. */
    private fun inside(band: Int, meters: Double?): Double {
        val low = if (band == 0) 0.0 else EDGES[band - 1]
        val high = if (band < EDGES.size) EDGES[band] * INSIDE_SHARE else Double.MAX_VALUE
        val value = meters ?: high
        return value.coerceIn(low, high)
    }

    private companion object {
        /** The upper edge of bands 0 to 2, as [MapLayout.ring] cuts them. */
        val EDGES = doubleArrayOf(1.0, 5.0, 10.0)
        const val EDGE_MARGIN = 1.15
        const val DWELL_MS = 2_000L
        const val FORGET_MS = 60_000L
        const val INSIDE_SHARE = 0.999
    }
}
