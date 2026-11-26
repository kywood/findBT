package com.gamenuri.btscanner24.core.model

/** The unit distances are shown in. Estimates stay in metres inside the app; only the text changes (#83). */
enum class DistanceUnit {
    METERS,
    FEET,
    ;

    companion object {
        /** Regions where people talk about short distances in feet: US, UK, Liberia, Myanmar. */
        private val FEET_REGIONS = setOf("US", "GB", "LR", "MM")

        /** The default until the user picks a unit in Settings; [region] is an ISO 3166 code such as "US". */
        fun forRegion(region: String): DistanceUnit = if (region.uppercase() in FEET_REGIONS) FEET else METERS
    }
}
