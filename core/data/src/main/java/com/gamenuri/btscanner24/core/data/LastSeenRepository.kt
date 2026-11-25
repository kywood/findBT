package com.gamenuri.btscanner24.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gamenuri.btscanner24.core.model.LastSeen
import com.gamenuri.btscanner24.core.model.LastSeenCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// The file's name is listed in the app's backup rules: places never leave the phone, not even in a backup.
private val Context.lastSeenStore: DataStore<Preferences> by preferencesDataStore(name = "last_seen")

/** Demo launches (debug screenshots) write here, so nothing made up lands in the user's own records. */
private val Context.demoLastSeenStore: DataStore<Preferences> by preferencesDataStore(name = "last_seen_demo")

/**
 * When and where this phone last heard each of the user's own devices (#162): one record per device.
 * What the recorder and the screens need of [LastSeenRepository], so they can be tested without a file.
 */
interface LastSeenStore {
    /** By the address the device is listed under. */
    val lastSeen: Flow<Map<String, LastSeen>>

    /** Writes [records] over whatever was kept for the same devices. */
    suspend fun record(records: Collection<LastSeen>)

    suspend fun remove(addresses: Collection<String>)

    suspend fun remove(address: String) = remove(listOf(address))

    /**
     * Whether the user turned place recording on (#178). Off until they do, on every Android version:
     * having the location permission (which the scan itself needs on Android 11 and older) is not consent.
     */
    val recordsPlace: Flow<Boolean>

    suspend fun setRecordsPlace(on: Boolean)

    /** Drops the place from every record and keeps the time, when the user turns recording off and says so. */
    suspend fun forgetPlaces()
}

/** [LastSeenStore] in a file on the phone only, gone when the device is taken off My devices. */
class LastSeenRepository(private val store: DataStore<Preferences>) : LastSeenStore {

    constructor(context: Context, demo: Boolean = false) : this(
        context.applicationContext.let { if (demo) it.demoLastSeenStore else it.lastSeenStore },
    )

    override val lastSeen: Flow<Map<String, LastSeen>> = store.data.map { LastSeenCodec.decode(it[RECORDS].orEmpty()) }

    override suspend fun record(records: Collection<LastSeen>) {
        if (records.isEmpty()) return
        store.edit { preferences ->
            val all = LastSeenCodec.decode(preferences[RECORDS].orEmpty()) + records.associateBy { it.address }
            preferences[RECORDS] = LastSeenCodec.encode(all.values)
        }
    }

    override suspend fun remove(addresses: Collection<String>) {
        if (addresses.isEmpty()) return
        store.edit { preferences ->
            val others = LastSeenCodec.decode(preferences[RECORDS].orEmpty()) - addresses.toSet()
            preferences[RECORDS] = LastSeenCodec.encode(others.values)
        }
    }

    override val recordsPlace: Flow<Boolean> = store.data.map { it[RECORDS_PLACE] ?: false }

    override suspend fun setRecordsPlace(on: Boolean) {
        store.edit { preferences -> preferences[RECORDS_PLACE] = on }
    }

    override suspend fun forgetPlaces() {
        store.edit { preferences ->
            val records = LastSeenCodec.decode(preferences[RECORDS].orEmpty())
            preferences[RECORDS] = LastSeenCodec.encode(records.values.map { it.copy(place = null) })
        }
    }

    private companion object {
        val RECORDS = stringPreferencesKey("records")
        val RECORDS_PLACE = booleanPreferencesKey("records_place")
    }
}
