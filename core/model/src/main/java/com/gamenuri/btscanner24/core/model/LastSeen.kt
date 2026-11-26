package com.gamenuri.btscanner24.core.model

/**
 * When, and if known where, this phone last heard one of the user's own devices (#162). One record
 * per device, the newest only. Kept on the phone and never sent anywhere (#19).
 */
data class LastSeen(
    /** The paired or saved address the device is listed under, not its changing advertising address. */
    val address: String,
    val seenAt: Long,
    /** Where the phone was then; null when it had no location permission or no fix. */
    val place: Place? = null,
) {
    /** The phone's own position, not the device's: the device was within Bluetooth range of it. */
    data class Place(val latitude: Double, val longitude: Double, val accuracyMeters: Float? = null) {
        companion object {
            /** How closely a place is kept: about a city block. */
            const val COARSE_METERS = 100f
            private const val COARSE_STEPS_PER_DEGREE = 1_000.0

            /**
             * The place as it is stored (#178): rounded to three decimals of a degree, about 100 m, however
             * exactly the phone knew it. Enough to say "at the office" or "at the gym", not which desk.
             */
            fun coarse(latitude: Double, longitude: Double, accuracyMeters: Float?): Place = Place(
                latitude = Math.round(latitude * COARSE_STEPS_PER_DEGREE) / COARSE_STEPS_PER_DEGREE,
                longitude = Math.round(longitude * COARSE_STEPS_PER_DEGREE) / COARSE_STEPS_PER_DEGREE,
                accuracyMeters = maxOf(accuracyMeters ?: COARSE_METERS, COARSE_METERS),
            )
        }
    }
}

/** When a sighting is worth writing down, so a device that stays nearby does not rewrite its record every second. */
object LastSeenPolicy {
    const val MIN_GAP_MS = 60_000L

    /** A place this fresh still stands for a new sighting that came without one. */
    const val PLACE_KEEP_MS = 5 * 60_000L

    fun isDue(previous: LastSeen?, seenAt: Long): Boolean = previous == null || seenAt - previous.seenAt >= MIN_GAP_MS

    /**
     * The record for a sighting at [seenAt]. Without a [place] of its own it keeps the previous one
     * while that is fresh: the device has been within range the whole time, so the phone cannot have
     * left it far behind.
     */
    fun next(previous: LastSeen?, address: String, seenAt: Long, place: LastSeen.Place?): LastSeen {
        val kept = previous?.place?.takeIf { seenAt - previous.seenAt <= PLACE_KEEP_MS }
        return LastSeen(address, seenAt, place ?: kept)
    }
}

/** Last-seen records as text for storage: one per line, tab-separated, the format's version first. */
object LastSeenCodec {
    private const val VERSION = "1"
    private const val FIELDS = 6

    fun encode(records: Collection<LastSeen>): String = records.joinToString("\n") { record ->
        val place = record.place
        listOf(
            VERSION,
            record.address,
            record.seenAt.toString(),
            place?.latitude?.toString().orEmpty(),
            place?.longitude?.toString().orEmpty(),
            place?.accuracyMeters?.toString().orEmpty(),
        ).joinToString("\t")
    }

    /** By address. Lines that cannot be read (a future format, a damaged file) are skipped, never fatal. */
    fun decode(text: String): Map<String, LastSeen> =
        text.lineSequence().mapNotNull(::decodeLine).associateBy { it.address }

    private fun decodeLine(line: String): LastSeen? {
        val parts = line.split('\t')
        if (parts.size != FIELDS || parts[0] != VERSION || parts[1].isBlank()) return null
        val seenAt = parts[2].toLongOrNull() ?: return null
        val latitude = parts[3].toDoubleOrNull()
        val longitude = parts[4].toDoubleOrNull()
        val place = if (latitude != null && longitude != null) {
            LastSeen.Place(latitude, longitude, parts[5].toFloatOrNull())
        } else {
            null
        }
        return LastSeen(parts[1], seenAt, place)
    }
}
