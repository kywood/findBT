package com.gamenuri.btscanner24.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gamenuri.btscanner24.core.model.SavedDevice
import com.gamenuri.btscanner24.core.model.SavedDeviceCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.savedDevicesStore: DataStore<Preferences> by preferencesDataStore(name = "saved_devices")

/** Demo launches (debug screenshots) save here, so nothing made up lands in the user's own list. */
private val Context.demoSavedDevicesStore: DataStore<Preferences> by preferencesDataStore(name = "saved_devices_demo")

/**
 * Devices the user saved to My devices by hand (#108), oldest first. Stored on the phone only.
 * The free limit is the caller's business (SaveLimit); nothing saved is ever dropped here.
 */
class SavedDevicesRepository(private val store: DataStore<Preferences>) {

    constructor(context: Context, demo: Boolean = false) : this(
        context.applicationContext.let { if (demo) it.demoSavedDevicesStore else it.savedDevicesStore },
    )

    val saved: Flow<List<SavedDevice>> = store.data.map { SavedDeviceCodec.decode(it[DEVICES].orEmpty()) }

    /** Adds [device], or replaces the one saved under the same address (a rename or a new type). */
    suspend fun save(device: SavedDevice) {
        store.edit { preferences ->
            val others = SavedDeviceCodec.decode(preferences[DEVICES].orEmpty()).filter { it.address != device.address }
            preferences[DEVICES] = SavedDeviceCodec.encode(others + device)
        }
    }

    suspend fun remove(address: String) {
        store.edit { preferences ->
            val others = SavedDeviceCodec.decode(preferences[DEVICES].orEmpty()).filter { it.address != address }
            preferences[DEVICES] = SavedDeviceCodec.encode(others)
        }
    }

    private companion object {
        val DEVICES = stringPreferencesKey("devices")
    }
}
