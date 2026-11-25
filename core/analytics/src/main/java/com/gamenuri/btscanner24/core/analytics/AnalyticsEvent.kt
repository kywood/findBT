package com.gamenuri.btscanner24.core.analytics

/**
 * Everything Nearo may report (#33), for the plan's KPIs: permission rate, find start and success
 * rate, save rate. Aggregate events only (#19): an event can carry nothing but the fixed words
 * below, so no device name, address, signal strength, maker, UUID or location can ever be put in
 * one. Add a new event here; never pass free text.
 */
sealed class AnalyticsEvent(val name: String, val params: Map<String, String> = emptyMap()) {
    /** The answer to the Bluetooth (or, on Android 11, location) permission request. */
    class Permission(granted: Boolean) :
        AnalyticsEvent("permission_result", mapOf("result" to if (granted) "granted" else "denied"))

    /** Find mode opened. */
    class FindStarted(from: FindSource) : AnalyticsEvent("find_started", mapOf("source" to from.value))

    /** "Found it" was pressed. */
    class FindFound(time: FindTime) : AnalyticsEvent("find_found", mapOf("time" to time.value))

    /** A device was saved to My devices; a count, nothing about the device. */
    data object DeviceSaved : AnalyticsEvent("device_saved")

    /** The free save limit was reached and the locked sheet shown. */
    data object SaveLockedShown : AnalyticsEvent("save_locked_shown")

    data object PaywallShown : AnalyticsEvent("paywall_shown")

    /** A rewarded ad was watched to the end and the 24 h pass started. */
    data object DayPassStarted : AnalyticsEvent("day_pass_started")
}

enum class FindSource(val value: String) { NEARBY("nearby"), MY_DEVICES("my_devices") }

/** How long finding took, in wide bands. */
enum class FindTime(val value: String) {
    UNDER_1_MIN("under_1_min"),
    UNDER_3_MIN("under_3_min"),
    UNDER_10_MIN("under_10_min"),
    LONGER("longer"),
    ;

    companion object {
        fun of(seconds: Int): FindTime = when {
            seconds < 60 -> UNDER_1_MIN
            seconds < 180 -> UNDER_3_MIN
            seconds < 600 -> UNDER_10_MIN
            else -> LONGER
        }
    }
}
