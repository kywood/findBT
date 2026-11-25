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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.protectionStore: DataStore<Preferences> by preferencesDataStore(name = "protection")

/**
 * Protection mode (#163): whether it is on, the devices it watches, mutes and standing alerts. The
 * protection service writes [setEnabled] and the alerts; the screens write the rest. On the phone only.
 */
class ProtectionRepository(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.protectionStore)

    val settings: Flow<ProtectionSettings> = store.data.map { preferences ->
        ProtectionSettings(
            enabled = preferences[ENABLED] ?: false,
            watched = preferences[WATCHED].orEmpty(),
            mutedUntil = TimesCodec.decode(preferences[MUTED].orEmpty()),
            alerts = TimesCodec.decode(preferences[ALERTS].orEmpty()),
        )
    }

    /** Off also drops the standing alerts: nothing is being watched any more. */
    suspend fun setEnabled(enabled: Boolean) {
        store.edit { preferences ->
            preferences[ENABLED] = enabled
            if (!enabled) preferences.remove(ALERTS)
        }
    }

    suspend fun setWatched(address: String, watched: Boolean) {
        store.edit { preferences ->
            val current = preferences[WATCHED].orEmpty()
            preferences[WATCHED] = if (watched) current + address else current - address
            if (!watched) preferences.dropTimes(address)
        }
    }

    /** The first time protection is turned on every device offered is watched; later the user's choice stands. */
    suspend fun watchIfUnset(addresses: Set<String>) {
        store.edit { preferences -> if (preferences[WATCHED] == null) preferences[WATCHED] = addresses }
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
        val MUTED = stringPreferencesKey("muted")
        val ALERTS = stringPreferencesKey("alerts")
    }
}
