package com.gamenuri.btscanner24.core.model

enum class DeviceSort { NEAREST, NAME, RECENT }

/** Device list sort and filter choices, remembered across app launches. */
data class ListPreferences(val sort: DeviceSort = DeviceSort.NEAREST, val filter: DeviceFilter = DeviceFilter())
