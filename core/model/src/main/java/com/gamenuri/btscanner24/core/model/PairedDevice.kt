package com.gamenuri.btscanner24.core.model

/** A device already paired (bonded) with this phone. My Devices lists these without any sign-up step. */
data class PairedDevice(
    val address: String,
    /** What the phone shows: the name the user gave it in Bluetooth settings, else the device's own. */
    val name: String?,
    val category: DeviceCategory,
    /** The device's own name when the user renamed it; that is the one it advertises (#116). */
    val advertisedName: String? = null,
) {
    /**
     * Things people lose come first (earbuds, headphones, watches, bands, health, trackers);
     * cars, PCs and the like go in the collapsed "Other paired devices" group.
     */
    val isPriority: Boolean
        get() = category in PRIORITY

    private companion object {
        val PRIORITY = setOf(
            DeviceCategory.EARBUDS,
            DeviceCategory.HEADPHONES,
            DeviceCategory.HEARING_AID,
            DeviceCategory.WATCH,
            DeviceCategory.FITNESS_BAND,
            DeviceCategory.TRACKER,
            DeviceCategory.HEALTH,
            DeviceCategory.SPEAKER,
        )
    }
}
