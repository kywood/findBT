package com.gamenuri.btscanner24.core.model

/** Distance chips of the filter (#85): within 5 m, 5–10 m, 10 m and more. */
enum class DistanceBucket(val upToMeters: Double) {
    WITHIN_5_M(5.0),
    FIVE_TO_TEN_M(10.0),
    OVER_10_M(Double.POSITIVE_INFINITY),
    ;

    companion object {
        fun of(meters: Double): DistanceBucket = entries.first { meters < it.upToMeters }
    }
}

/**
 * What the Nearby list shows (#82, #85, design nearby-filter-v1). Empty sets mean "no limit". The
 * defaults show every personal device and hide the rest ("Other devices").
 */
data class DeviceFilter(
    /** Beacons, TVs, sensors, PC accessories, unknown types (#82). Off by default. */
    val showOther: Boolean = false,
    /** On by default: unnamed trackers are often the very thing people look for (#62, #46 naming). */
    val showUnnamed: Boolean = true,
    val hideGone: Boolean = false,
    val types: Set<CategoryGroup> = emptySet(),
    val distances: Set<DistanceBucket> = emptySet(),
    val signals: Set<SignalLevel> = emptySet(),
    /** Company names as shown in the list (Bluetooth SIG names). */
    val manufacturers: Set<String> = emptySet(),
) {
    /** Badge on the filter button: each switch off its default counts 1, each chip group in use counts 1. */
    val activeCount: Int
        get() {
            val defaults = DeviceFilter()
            return listOf(
                showOther != defaults.showOther,
                showUnnamed != defaults.showUnnamed,
                hideGone != defaults.hideGone,
                types.isNotEmpty(),
                distances.isNotEmpty(),
                signals.isNotEmpty(),
                manufacturers.isNotEmpty(),
            ).count { it }
        }
}

/** Whether a device is something a person carries or uses (#82). */
enum class DeviceRelevance { PERSONAL, OTHER }

/** Rules table of design nearby-filter-v1 §3. */
object DeviceRelevanceRules {
    private val PERSONAL_TYPES = setOf(
        DeviceCategory.EARBUDS,
        DeviceCategory.HEADPHONES,
        DeviceCategory.HEARING_AID,
        DeviceCategory.SPEAKER,
        DeviceCategory.WATCH,
        DeviceCategory.FITNESS_BAND,
        DeviceCategory.TRACKER,
        DeviceCategory.GAMEPAD,
    )

    /**
     * Phones, tablets and laptops are personal, but other people's phones and PCs advertise all the
     * time without a name (Apple Continuity, Microsoft CDP), so only named ones count (PM, #82).
     */
    private val NAMED_ONLY_TYPES = setOf(DeviceCategory.PHONE, DeviceCategory.TABLET, DeviceCategory.COMPUTER)

    /**
     * Anything paired with this phone is personal, whatever its type. Beacons, TV, sensors, health
     * meters, cars, keyboards, mice and unknown types are "other".
     */
    fun of(device: BleDevice, isPaired: Boolean = false): DeviceRelevance {
        val personal = isPaired ||
            device.category in PERSONAL_TYPES ||
            (device.category in NAMED_ONLY_TYPES && (device.hasName || device.modelName != null))
        return if (personal) DeviceRelevance.PERSONAL else DeviceRelevance.OTHER
    }
}
