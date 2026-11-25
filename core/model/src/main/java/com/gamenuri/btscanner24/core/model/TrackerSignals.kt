package com.gamenuri.btscanner24.core.model

/**
 * One advertisement format that says "tag-style tracker". Exactly one of [companyId] (manufacturer
 * data), [serviceData16] (service data) and [serviceUuid16] (a listed service) is set; for the first
 * two the data has to start with [prefix] where [mask] has bits set.
 */
class TrackerSignal private constructor(
    val companyId: Int? = null,
    val serviceData16: Int? = null,
    val serviceUuid16: Int? = null,
    val prefix: ByteArray = ByteArray(0),
    val mask: ByteArray = ByteArray(0),
) {
    fun fits(data: ByteArray?): Boolean {
        if (data == null || data.size < prefix.size) return false
        return prefix.indices.all { i ->
            val bits = mask[i].toInt()
            (data[i].toInt() and bits) == (prefix[i].toInt() and bits)
        }
    }

    companion object {
        private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

        fun maker(companyId: Int, vararg prefix: Int) =
            TrackerSignal(companyId = companyId, prefix = bytes(*prefix), mask = ByteArray(prefix.size) { -1 })

        fun serviceData(uuid16: Int, vararg prefix: Int) =
            TrackerSignal(serviceData16 = uuid16, prefix = bytes(*prefix), mask = ByteArray(prefix.size) { -1 })

        fun service(uuid16: Int) = TrackerSignal(serviceUuid16 = uuid16)
    }
}

/**
 * The advertisements unknown-tracker detection (#79) listens for. Where a format tells, only the
 * "away from its owner" state is taken: a tag next to its owner's phone is somebody's keys on the
 * same bus, and the user's own tag next to their own phone is not worth an alert either.
 *
 * Taken from public write-ups of these formats and not yet checked against real tags (#79): the
 * Find My and SmartTag state bytes in particular. A format that turns out wrong is missed, or
 * shows up as an alert the user can answer with "I know this device".
 */
object TrackerSignals {
    const val APPLE = 0x004C
    const val SMARTTAG = 0xFD5A
    const val FIND_HUB = 0xFEAA
    const val TILE = 0xFEED
    const val TILE_OLD = 0xFEEC
    const val CHIPOLO = 0xFE33

    val all: List<TrackerSignal> = listOf(
        // Apple Find My, separated from its owner: type 0x12 with the full 25-byte key payload.
        // AirTags, and AirPods or other Find My accessories that were left or planted.
        TrackerSignal.maker(APPLE, 0x12, 0x19),
        // Samsung SmartTag in its three offline states (low three bits 1, 2, 3 under version 0x10).
        TrackerSignal.serviceData(SMARTTAG, 0x11),
        TrackerSignal.serviceData(SMARTTAG, 0x12),
        TrackerSignal.serviceData(SMARTTAG, 0x13),
        // Google Find Hub network tag in unwanted-tracking protection mode (frame 0x41).
        TrackerSignal.serviceData(FIND_HUB, 0x41),
        // Tile and Chipolo do not say whether the owner is near; their addresses do not change.
        TrackerSignal.service(TILE),
        TrackerSignal.service(TILE_OLD),
        TrackerSignal.service(CHIPOLO),
    )

    /** Whether an advertisement with this content is one of [all]. */
    fun matches(
        makerData: (companyId: Int) -> ByteArray?,
        serviceData: (uuid16: Int) -> ByteArray?,
        serviceUuids16: Collection<Int>,
    ): Boolean = all.any { signal ->
        when {
            signal.companyId != null -> signal.fits(makerData(signal.companyId))
            signal.serviceData16 != null -> signal.fits(serviceData(signal.serviceData16))
            else -> signal.serviceUuid16 in serviceUuids16
        }
    }
}
