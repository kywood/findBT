package com.gamenuri.btscanner24.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gamenuri.btscanner24.core.model.CategoryGroup
import com.gamenuri.btscanner24.core.model.DeviceSort
import com.gamenuri.btscanner24.core.model.ListPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.listPreferencesStore: DataStore<Preferences> by preferencesDataStore(name = "list_preferences")

/** Persists the device list sort and filter so the next launch opens the same way. */
class ListPreferencesRepository(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.listPreferencesStore)

    val preferences: Flow<ListPreferences> = store.data.map { it.toListPreferences() }

    suspend fun update(transform: (ListPreferences) -> ListPreferences) {
        store.edit { prefs ->
            val next = transform(prefs.toListPreferences())
            prefs[SORT] = next.sort.name
            prefs[NAMED_ONLY] = next.namedOnly
            prefs[GROUPS] = next.groups.mapTo(HashSet()) { it.name }
            prefs[HIDE_GONE] = next.hideGone
        }
    }

    private fun Preferences.toListPreferences(): ListPreferences {
        val defaults = ListPreferences()
        return ListPreferences(
            sort = this[SORT]?.let { name -> DeviceSort.entries.firstOrNull { it.name == name } } ?: defaults.sort,
            namedOnly = this[NAMED_ONLY] ?: defaults.namedOnly,
            groups = this[GROUPS]
                ?.mapNotNullTo(HashSet()) { name -> CategoryGroup.entries.firstOrNull { it.name == name } }
                ?: defaults.groups,
            hideGone = this[HIDE_GONE] ?: defaults.hideGone,
        )
    }

    private companion object {
        val SORT = stringPreferencesKey("sort")
        val NAMED_ONLY = booleanPreferencesKey("named_only")
        val GROUPS = stringSetPreferencesKey("groups")
        val HIDE_GONE = booleanPreferencesKey("hide_gone")
    }
}

/** Implemented by the Application; ViewModel factories read data dependencies through it. */
interface DataDependenciesProvider {
    val listPreferences: ListPreferencesRepository
}
