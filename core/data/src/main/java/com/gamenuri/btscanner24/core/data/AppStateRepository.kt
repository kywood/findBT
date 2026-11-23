package com.gamenuri.btscanner24.core.data

import android.content.Context

/**
 * Small app-wide flags read before the first frame (onboarding, #30). SharedPreferences rather than
 * DataStore so the start screen is known synchronously and cold start doesn't wait on a flow (#34).
 */
class AppStateRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    val onboardingDone: Boolean get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }

    private companion object {
        const val FILE = "app_state"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}
