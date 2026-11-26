package com.gamenuri.btscanner24.core.model

/**
 * Protection mode as the user set it (#163, #79): whether it is on, which of their devices raise a
 * left-behind alert, which are muted for now, which alerts are still standing, and the same for
 * trackers that are not theirs. On the phone only.
 */
data class ProtectionSettings(
    val enabled: Boolean = false,
    /** Addresses, as listed on My devices, of the devices the user switched on. */
    val watched: Set<String> = emptySet(),
    /** The devices the user switched off, e.g. one that stays at home on purpose. */
    val unwatched: Set<String> = emptySet(),
    /** No alert for a device before this time. */
    val mutedUntil: Map<String, Long> = emptyMap(),
    /** Devices that may have been left behind, with the time each was last heard; gone when heard again. */
    val alerts: Map<String, Long> = emptyMap(),
    /** Whether trackers that are not the user's are looked for (#79). Off until the user turns it on: a test feature in 2.0 (#199). */
    val trackers: Boolean = false,
    /** Trackers that may be moving with the user, newest first; none is kept beyond [TrackerWatch.KEEP_MS]. */
    val trackerAlerts: List<TrackerAlert> = emptyList(),
    /** Trackers the user said they know, with when they said so: never alerted about again. */
    val knownTrackers: Map<String, Long> = emptyMap(),
) {
    /**
     * Whether [address] raises a left-behind alert. Until the user touches its switch a device they
     * saved is watched and one that is only paired with the phone is not.
     */
    fun watches(address: String, isSaved: Boolean): Boolean = address in watched || (isSaved && address !in unwatched)
}

/** Address-to-time pairs as text for storage: one per line, tab-separated. */
object TimesCodec {
    fun encode(times: Map<String, Long>): String = times.entries.joinToString("\n") { "${it.key}\t${it.value}" }

    /** Lines that cannot be read are skipped, never fatal. */
    fun decode(text: String): Map<String, Long> = text.lineSequence().mapNotNull { line ->
        val parts = line.split('\t')
        val time = parts.getOrNull(1)?.toLongOrNull()
        if (parts.size == 2 && parts[0].isNotBlank() && time != null) parts[0] to time else null
    }.toMap()
}

/**
 * Decides when a watched device "may have been left behind" (#163). A false alert costs more than a
 * late one, so every doubt counts against alerting:
 *
 * - the device was heard at least once since watching began, and not for [GRACE_MS];
 * - it is not connected to the phone;
 * - the watch could hear the whole time (no Bluetooth-off or failed scan since it was last heard);
 * - the phone itself moved around the time the signal went or after it: a phone lying still next to
 *   earbuds that were put in their case has left nothing behind. Phones without a motion sensor skip
 *   this check;
 * - no alert is standing for it already and the user did not mute it.
 *
 * Not thread safe: call from one thread.
 */
class LeftBehindWatch(private val motionKnown: Boolean) {
    private val lastHeard = HashMap<String, Long>()
    private val alerted = HashSet<String>()
    private var movedAt: Long? = null
    private var blindAt: Long? = null

    /** An alert that should be shown now. */
    data class Alert(val address: String, val lastHeardAt: Long)

    /** Heard in a scan or found connected. Returns true when that ends a standing alert. */
    fun onHeard(address: String, at: Long): Boolean {
        lastHeard[address] = maxOf(at, lastHeard[address] ?: Long.MIN_VALUE)
        return alerted.remove(address)
    }

    /** The phone started moving (walking, in a vehicle). */
    fun onMoved(at: Long) {
        movedAt = at
    }

    /** The watch could not hear at this moment: Bluetooth off, Location off, a failed scan. */
    fun onBlind(at: Long) {
        blindAt = at
    }

    /** Stops watching [address], e.g. when its switch goes off or it is taken off My devices. */
    fun forget(address: String) {
        lastHeard.remove(address)
        alerted.remove(address)
    }

    fun lastHeardAt(address: String): Long? = lastHeard[address]

    /** The devices to alert about now; each is returned once until it is heard again. */
    fun due(watched: Set<String>, connected: Set<String>, mutedUntil: Map<String, Long>, now: Long): List<Alert> =
        watched.mapNotNull { address ->
            val heardAt = lastHeard[address] ?: return@mapNotNull null
            val blind = blindAt
            val moved = movedAt
            when {
                address in connected || address in alerted -> null
                now - heardAt < GRACE_MS -> null
                (mutedUntil[address] ?: Long.MIN_VALUE) > now -> null
                blind != null && blind >= heardAt -> null
                motionKnown && (moved == null || moved < heardAt - MOVE_SLACK_MS) -> null
                else -> Alert(address, heardAt).also { alerted += address }
            }
        }

    companion object {
        /** A signal that comes back within this time was a gap, not a device left behind. */
        const val GRACE_MS = 3 * 60_000L

        /** Walking away starts a little before the signal is lost. */
        const val MOVE_SLACK_MS = 2 * 60_000L
    }
}
