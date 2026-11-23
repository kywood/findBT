package com.gamenuri.btscanner24.core.model

enum class DeviceSort { NEAREST, NAME, RECENT }

/** Device list sort and filter choices, remembered across app launches. */
data class ListPreferences(
    val sort: DeviceSort = DeviceSort.NEAREST,
    /** Off by default: unnamed trackers are often the very thing people look for (#62, #46 naming). */
    val namedOnly: Boolean = false,
    /** Empty means every group is shown. */
    val groups: Set<CategoryGroup> = emptySet(),
    val hideGone: Boolean = false,
)
