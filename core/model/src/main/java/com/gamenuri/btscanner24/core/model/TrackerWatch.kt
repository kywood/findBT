package com.gamenuri.btscanner24.core.model

/** A tracker that is not the user's and may be moving with them (#79), as it is kept and shown. */
data class TrackerAlert(
    /** The address it advertised from; trackers change it, usually once a day when away from their owner. */
    val address: String,
    /** When it was first heard while the phone was on the move. */
    val firstHeardAt: Long,
    val lastHeardAt: Long,
)

/** Tracker alerts as text for storage: one per line, tab-separated. */
object TrackerAlertCodec {
    fun encode(alerts: Collection<TrackerAlert>): String =
        alerts.joinToString("\n") { "${it.address}\t${it.firstHeardAt}\t${it.lastHeardAt}" }

    /** Lines that cannot be read are skipped, never fatal. */
    fun decode(text: String): List<TrackerAlert> = text.lineSequence().mapNotNull { line ->
        val parts = line.split('\t')
        val first = parts.getOrNull(1)?.toLongOrNull()
        val last = parts.getOrNull(2)?.toLongOrNull()
        if (parts.size == 3 && parts[0].isNotBlank() && first != null && last != null) {
            TrackerAlert(parts[0], first, last)
        } else {
            null
        }
    }.toList()
}

/**
 * Decides when a tracker that is not the user's "may be moving with them" (#79), without knowing
 * where the phone is: only that it moved, and how long ago. A false alert frightens people, so the
 * rule is strict and anything that cannot be followed is dropped rather than guessed at:
 *
 * - only sightings while the phone is on the move count: a neighbour's tag heard from the sofa never does;
 * - the same address has to be heard on at least [MIN_TRIPS] separate stretches of movement, over at
 *   least [MIN_SPAN_MS], and still be around now;
 * - a tracker that goes silent for [GAP_RESET_MS] starts from nothing: one carried along is heard all
 *   the time, one passed twice a day at the front door is not;
 * - weak signals are ignored: something in a bag or a pocket is close;
 * - a tracker that changes its address in between is a new one here. Those are missed, not guessed.
 *
 * Kept in memory only and for [KEEP_MS] at most. Not thread safe: call from one thread.
 */
class TrackerWatch {
    private class Track(var lastHeardAt: Long) {
        var firstMovingAt: Long? = null
        var lastMovingAt: Long? = null
        val trips = HashSet<Int>()
        var alerted = false
    }

    private val tracks = HashMap<String, Track>()
    private var trip = 0
    private var tripStartedAt: Long? = null
    private var movedAt: Long? = null

    /** The phone started moving (walking, in a vehicle); reported again and again while it keeps moving. */
    fun onMoved(at: Long) {
        val started = tripStartedAt
        if (started == null || at - started >= TRIP_MS || at < started) {
            trip++
            tripStartedAt = at
        }
        movedAt = at
    }

    /** A tracker that is not one of the user's own devices was heard. */
    fun onHeard(address: String, at: Long, rssi: Int) {
        if (rssi < MIN_RSSI) return
        if (address !in tracks && tracks.size >= MAX_TRACKS) dropOldest()
        val track = tracks.getOrPut(address) { Track(at) }
        if (at - track.lastHeardAt > GAP_RESET_MS) {
            track.firstMovingAt = null
            track.trips.clear()
            track.alerted = false
        }
        track.lastHeardAt = maxOf(at, track.lastHeardAt)
        val moved = movedAt ?: return
        if (at - moved in 0..MOVING_MS) {
            track.firstMovingAt = track.firstMovingAt ?: at
            track.lastMovingAt = at
            track.trips += trip
        }
    }

    /** Stops following [address], e.g. when the user says they know it. */
    fun forget(address: String) {
        tracks.remove(address)
    }

    /** When [address] was last heard, for keeping a standing alert up to date. */
    fun lastHeardAt(address: String): Long? = tracks[address]?.lastHeardAt

    /** The trackers to alert about now; each is returned once until it has been away and come back. */
    fun due(known: Set<String>, now: Long): List<TrackerAlert> {
        tracks.values.removeAll { now - it.lastHeardAt > KEEP_MS }
        return tracks.mapNotNull { (address, track) ->
            val first = track.firstMovingAt
            when {
                track.alerted || address in known || first == null -> null
                track.trips.size < MIN_TRIPS -> null
                (track.lastMovingAt ?: first) - first < MIN_SPAN_MS -> null
                now - track.lastHeardAt > HERE_MS -> null
                else -> TrackerAlert(address, first, track.lastHeardAt).also { track.alerted = true }
            }
        }
    }

    private fun dropOldest() {
        tracks.minByOrNull { it.value.lastHeardAt }?.let { tracks.remove(it.key) }
    }

    companion object {
        /** Stretches of movement at least this far apart count as separate ones. */
        const val TRIP_MS = 10 * 60_000L

        /** Heard this soon after the phone last reported moving counts as "while on the move". */
        const val MOVING_MS = 2 * 60_000L
        const val MIN_TRIPS = 3
        const val MIN_SPAN_MS = 30 * 60_000L

        /** Heard within this time counts as "still here". */
        const val HERE_MS = 5 * 60_000L
        const val GAP_RESET_MS = 30 * 60_000L

        /** Nothing about a tracker is kept longer than this. */
        const val KEEP_MS = 48 * 60 * 60_000L

        /** About ten metres indoors; weaker than this is someone else's, somewhere else. */
        const val MIN_RSSI = -85
        private const val MAX_TRACKS = 300
    }
}
