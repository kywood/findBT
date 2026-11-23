package com.gamenuri.btscanner24.core.model

/** A device already paired (bonded) with this phone. My Devices lists these without any sign-up step. */
data class PairedDevice(
    val address: String,
    val name: String?,
    val category: DeviceCategory,
) {
    /**
     * Things people lose come first (earbuds, headphones, watches, bands, health, trackers);
     * cars, PCs and the like go in the collapsed "Other paired devices" group.
     */
    val isPriority: Boolean
        get() = category.filterGroup != CategoryGroup.OTHER || category == DeviceCategory.HEALTH ||
            category == DeviceCategory.SPEAKER
}
