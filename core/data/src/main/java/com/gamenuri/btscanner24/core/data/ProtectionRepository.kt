package com.gamenuri.btscanner24.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gamenuri.btscanner24.core.model.ProtectionSettings
import com.gamenuri.btscanner24.core.model.TimesCodec
import com.gamenuri.btscanner24.core.model.TrackerAlert
import com.gamenuri.btscanner24.core.model.TrackerAlertCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.protectionStore: DataStore<Preferences> by preferencesDataStore(name = "protection")

/**
 * Protection mode (#163, #79): whether it is on, the devices it watches, mutes, standing alerts and
 * the trackers that are not the user's. The
 * protection service writes [setEnabled] and the alerts; the screens write the rest. On the phone only.
 */
class ProtectionRepository(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.protectionStore)

    val settings: Flow<ProtectionSettings> = store.data.map { preferences ->
        ProtectionSettings(
            enabled = preferences[ENABLED] ?: false,
            watched = preferences[WATCHED].orEmpty(),
            unwatched = preferences[UNWATCHED].orEmpty(),
            mutedUntil = TimesCodec.decode(preferences[MUTED].orEmpty()),
            alerts = TimesCodec.decode(preferences[ALERTS].orEmpty()),
            trackers = preferences[TRACKERS] ?: true,
            trackerAlerts = TrackerAlertCodec.decode(preferences[TRACKER_ALERTS].orEmpty())
                .sortedByDescending { it.lastHeardAt },
            knownTrackers = TimesCodec.decode(preferences[KNOWN_TRACKERS].orEmpty()),
        )
    }

    /** Off also drops the standing alerts: nothing is being watched any more. */
    suspend fun setEnabled(enabled: Boolean) {
        store.edit { preferences ->
            preferences[ENABLED] = enabled
            if (!enabled) {
                preferences.remove(ALERTS)
                preferences.remove(TRACKER_ALERTS)
            }
        }
    }

    /** Off also drops the tracker alerts that were standing. */
    suspend fun setTrackers(on: Boolean) {
        store.edit { preferences ->
            preferences[TRACKERS] = on
            if (!on) preferences.remove(TRACKER_ALERTS)
        }
    }

    /**
     * Keeps [alert], over an older one for the same address, and drops every alert whose tracker was
     * last heard before [keepSince]: nothing about a stranger's tracker stays for long.
     */
    suspend fun keepTrackerAlert(alert: TrackerAlert, keepSince: Long) {
        store.edit { preferences ->
            val others = TrackerAlertCodec.decode(preferences[TRACKER_ALERTS].orEmpty())
                .filter { it.address != alert.address && it.lastHeardAt >= keepSince }
            preferences[TRACKER_ALERTS] = TrackerAlertCodec.encode(others + alert)
        }
    }

    /** Drops the alerts whose tracker was last heard [before] that time. */
    suspend fun dropTrackerAlerts(before: Long) {
        store.edit { preferences ->
            val kept = TrackerAlertCodec.decode(preferences[TRACKER_ALERTS].orEmpty()).filter { it.lastHeardAt >= before }
            preferences[TRACKER_ALERTS] = TrackerAlertCodec.encode(kept)
        }
    }

    /** "I know this device": its alert goes and it is never alerted about again. */
    suspend fun markTrackerKnown(address: String, at: Long) {
        store.edit { preferences ->
            val known = TimesCodec.decode(preferences[KNOWN_TRACKERS].orEmpty()) + (address to at)
            val newest = known.entries.sortedByDescending { it.value }.take(MAX_KNOWN_TRACKERS)
            preferences[KNOWN_TRACKERS] = TimesCodec.encode(newest.associate { it.key to it.value })
            val alerts = TrackerAlertCodec.decode(preferences[TRACKER_ALERTS].orEmpty())
            preferences[TRACKER_ALERTS] = TrackerAlertCodec.encode(alerts.filter { it.address != address })
        }
    }

    suspend fun setWatched(address: String, watched: Boolean) {
        store.edit { preferences ->
            val on = preferences[WATCHED].orEmpty()
            val off = preferences[UNWATCHED].orEmpty()
            preferences[WATCHED] = if (watched) on + address else on - address
            preferences[UNWATCHED] = if (watched) off - address else off + address
            if (!watched) preferences.dropTimes(address)
        }
    }

    suspend fun mute(address: String, until: Long) {
        store.edit { preferences ->
            preferences[MUTED] = TimesCodec.encode(TimesCodec.decode(preferences[MUTED].orEmpty()) + (address to until))
            preferences[ALERTS] = TimesCodec.encode(TimesCodec.decode(preferences[ALERTS].orEmpty()) - address)
        }
    }

    /** A device that may have been left behind, last heard at [lastHeardAt]. */
    suspend fun raiseAlert(address: String, lastHeardAt: Long) {
        store.edit { preferences ->
            val alerts = TimesCodec.decode(preferences[ALERTS].orEmpty()) + (address to lastHeardAt)
            preferences[ALERTS] = TimesCodec.encode(alerts)
        }
    }

    suspend fun clearAlert(address: String) {
        store.edit { preferences ->
            val alerts = TimesCodec.decode(preferences[ALERTS].orEmpty())
            if (address in alerts) preferences[ALERTS] = TimesCodec.encode(alerts - address)
        }
    }

    /** Everything kept about [address], when it is taken off My devices. */
    suspend fun forget(address: String) {
        store.edit { preferences ->
            preferences[WATCHED]?.let { preferences[WATCHED] = it - address }
            preferences[UNWATCHED]?.let { preferences[UNWATCHED] = it - address }
            preferences.dropTimes(address)
        }
    }

    private fun MutablePreferences.dropTimes(address: String) {
        this[MUTED] = TimesCodec.encode(TimesCodec.decode(this[MUTED].orEmpty()) - address)
        this[ALERTS] = TimesCodec.encode(TimesCodec.decode(this[ALERTS].orEmpty()) - address)
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val WATCHED = stringSetPreferencesKey("watched")
        val UNWATCHED = stringSetPreferencesKey("unwatched")
        val MUTED = stringPreferencesKey("muted")
        val ALERTS = stringPreferencesKey("alerts")
        val TRACKERS = booleanPreferencesKey("trackers")
        val TRACKER_ALERTS = stringPreferencesKey("tracker_alerts")
        val KNOWN_TRACKERS = stringPreferencesKey("known_trackers")

        /** Trackers change their address, so old entries stop meaning anything: the newest are enough. */
        const val MAX_KNOWN_TRACKERS = 200
    }
}
