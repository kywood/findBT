package com.gamenuri.btscanner24

/**
 * A debug-only demo launch (#47), from `adb shell am start ... --es demo nearby|devices|find`.
 * Release builds never produce one: their DemoMode always returns null.
 */
internal data class DemoRequest(
    /** "nearby", "devices", "find", "onboarding", "settings", "settings_premium" or "paywall". */
    val screen: String,
    /** Find-screen state for screen "find", e.g. "spin", "locked", "here". */
    val findState: String? = null,
    /** BCP 47 tag such as "de" or "pt-BR", applied to this launch only. */
    val locale: String? = null,
    /** Onboarding page for screen "onboarding": intro, permission, denied (+ "_legacy" for Android 11 copy). */
    val onboardingPage: String? = null,
) {
    val startOnMyDevices: Boolean get() = screen == "devices"

    /** Settings for free users, with Premium already bought, or with the paywall sheet open (#56). */
    val startOnSettings: Boolean get() = screen == "settings" || screen == "settings_premium" || screen == "paywall"

    val openPaywall: Boolean get() = screen == "paywall"
}
