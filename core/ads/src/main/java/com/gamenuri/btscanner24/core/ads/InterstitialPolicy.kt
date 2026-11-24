package com.gamenuri.btscanner24.core.ads

import android.content.SharedPreferences
import java.time.Instant
import java.time.ZoneId

/**
 * When the full-screen ad after "Found it" → Done may show (#81 ③): at most [MAX_PER_DAY] a day,
 * [MIN_GAP_MS] apart, never on the install day and never after the day's first find.
 */
class InterstitialPolicy(
    private val storage: Storage,
    private val installedAt: Long,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    interface Storage {
        var epochDay: Long
        var findsToday: Int
        var shownToday: Int
        var lastShownAt: Long
    }

    /** Records a finished find; true if an ad may follow it. */
    fun onFindDone(now: Long): Boolean {
        val today = day(now)
        if (storage.epochDay != today) {
            storage.epochDay = today
            storage.findsToday = 0
            storage.shownToday = 0
        }
        storage.findsToday += 1
        return today != day(installedAt) &&
            storage.findsToday > 1 &&
            storage.shownToday < MAX_PER_DAY &&
            now - storage.lastShownAt >= MIN_GAP_MS
    }

    fun onShown(now: Long) {
        storage.shownToday += 1
        storage.lastShownAt = now
    }

    private fun day(millis: Long): Long = Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate().toEpochDay()

    class SharedPreferencesStorage(private val prefs: SharedPreferences) : Storage {
        override var epochDay: Long
            get() = prefs.getLong("day", Long.MIN_VALUE)
            set(value) = prefs.edit().putLong("day", value).apply()
        override var findsToday: Int
            get() = prefs.getInt("finds", 0)
            set(value) = prefs.edit().putInt("finds", value).apply()
        override var shownToday: Int
            get() = prefs.getInt("shown", 0)
            set(value) = prefs.edit().putInt("shown", value).apply()
        override var lastShownAt: Long
            get() = prefs.getLong("last", 0L)
            set(value) = prefs.edit().putLong("last", value).apply()
    }

    companion object {
        const val MAX_PER_DAY = 2
        const val MIN_GAP_MS = 10 * 60 * 1000L
    }
}
