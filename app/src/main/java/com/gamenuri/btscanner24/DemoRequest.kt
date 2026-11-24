package com.gamenuri.btscanner24

/**
 * A debug-only demo launch (#47), from `adb shell am start ... --es demo nearby|devices|find`.
 * Release builds never produce one: their DemoMode always returns null.
 */
internal data class DemoRequest(
    /** "nearby", "devices", "find" or "onboarding". */
    val screen: String,
    /** Find-screen state for screen "find", e.g. "spin", "locked", "here". */
    val findState: String? = null,
    /** BCP 47 tag such as "de" or "pt-BR", applied to this launch only. */
    val locale: String? = null,
    /** Onboarding page for screen "onboarding": intro, permission, denied (+ "_legacy" for Android 11 copy). */
    val onboardingPage: String? = null,
) {
    val startOnMyDevices: Boolean get() = screen == "devices"
}
