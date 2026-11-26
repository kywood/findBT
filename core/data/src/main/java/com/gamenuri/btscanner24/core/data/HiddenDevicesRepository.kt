package com.gamenuri.btscanner24.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.hiddenDevicesStore: DataStore<Preferences> by preferencesDataStore(name = "hidden_devices")

/** Paired devices the user removed from My Devices (⋮ → Hide). Stored on the phone only. */
class HiddenDevicesRepository(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.hiddenDevicesStore)

    val hidden: Flow<Set<String>> = store.data.map { it[HIDDEN].orEmpty() }

    suspend fun hide(address: String) {
        store.edit { it[HIDDEN] = it[HIDDEN].orEmpty() + address }
    }

    suspend fun unhide(address: String) {
        store.edit { it[HIDDEN] = it[HIDDEN].orEmpty() - address }
    }

    private companion object {
        val HIDDEN = stringSetPreferencesKey("addresses")
    }
}
