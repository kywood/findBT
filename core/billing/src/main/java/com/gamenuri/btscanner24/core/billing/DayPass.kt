package com.gamenuri.btscanner24.core.billing

import android.content.SharedPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The rewarded ad's 24 h of Premium (#81): at most one a day, kept on the phone only. A day is the
 * phone's local calendar day, so the next pass can start the day after the last one was granted.
 */
class DayPass(private val storage: Storage, private val zone: () -> ZoneId = ZoneId::systemDefault) {
    /** Two numbers; [SharedPreferencesStorage] in the app, a map in tests. */
    interface Storage {
        var until: Long
        var grantedOnEpochDay: Long
    }

    /** End of the running pass, or null. */
    fun until(now: Long): Long? = storage.until.takeIf { it > now }

    fun isAvailable(now: Long): Boolean = until(now) == null && storage.grantedOnEpochDay != day(now)

    /** Starts a pass; null when [isAvailable] is false. */
    fun grant(now: Long): Long? {
        if (!isAvailable(now)) return null
        val end = now + DURATION_MS
        storage.until = end
        storage.grantedOnEpochDay = day(now)
        return end
    }

    private fun day(now: Long): Long = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone()).toEpochDay()

    class SharedPreferencesStorage(private val prefs: SharedPreferences) : Storage {
        override var until: Long
            get() = prefs.getLong(KEY_UNTIL, 0L)
            set(value) = prefs.edit().putLong(KEY_UNTIL, value).apply()
        override var grantedOnEpochDay: Long
            get() = prefs.getLong(KEY_DAY, Long.MIN_VALUE)
            set(value) = prefs.edit().putLong(KEY_DAY, value).apply()
    }

    companion object {
        const val DURATION_MS = 24 * 60 * 60 * 1000L
        private const val KEY_UNTIL = "day_pass_until"
        private const val KEY_DAY = "day_pass_day"
    }
}
