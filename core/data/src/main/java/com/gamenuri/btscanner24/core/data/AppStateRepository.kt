package com.gamenuri.btscanner24.core.data

import android.content.Context
import com.gamenuri.btscanner24.core.model.DistanceUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private val _distanceUnit = MutableStateFlow(
        prefs.getString(KEY_DISTANCE_UNIT, null)?.let { name -> DistanceUnit.entries.firstOrNull { it.name == name } },
    )

    /** The unit picked in Settings; null until then, and the phone's region decides (#83). */
    val distanceUnit: StateFlow<DistanceUnit?> = _distanceUnit.asStateFlow()

    fun setDistanceUnit(unit: DistanceUnit) {
        prefs.edit().putString(KEY_DISTANCE_UNIT, unit.name).apply()
        _distanceUnit.value = unit
    }

    private companion object {
        const val FILE = "app_state"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_DISTANCE_UNIT = "distance_unit"
    }
}
