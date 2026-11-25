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
import com.gamenuri.btscanner24.core.model.DeviceFilter
import com.gamenuri.btscanner24.core.model.DeviceSort
import com.gamenuri.btscanner24.core.model.DistanceBucket
import com.gamenuri.btscanner24.core.model.ListPreferences
import com.gamenuri.btscanner24.core.model.SignalLevel
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
            val filter = next.filter
            prefs[SORT] = next.sort.name
            prefs[NAMED_ONLY] = !filter.showUnnamed
            prefs[GROUPS] = filter.types.mapTo(HashSet()) { it.name }
            prefs[HIDE_GONE] = filter.hideGone
            prefs[SHOW_OTHER] = filter.showOther
            prefs[DISTANCES] = filter.distances.mapTo(HashSet()) { it.name }
            prefs[MANUFACTURERS] = filter.manufacturers
            prefs[SIGNALS] = filter.signals.mapTo(HashSet()) { it.name }
        }
    }

    // Keys from before #82 keep their names, so existing users keep their choices.
    private fun Preferences.toListPreferences(): ListPreferences {
        val defaults = DeviceFilter()
        return ListPreferences(
            sort = this[SORT]?.let { name -> DeviceSort.entries.firstOrNull { it.name == name } } ?: DeviceSort.NEAREST,
            filter = DeviceFilter(
                showOther = this[SHOW_OTHER] ?: defaults.showOther,
                showUnnamed = this[NAMED_ONLY]?.not() ?: defaults.showUnnamed,
                types = this[GROUPS]
                    ?.mapNotNullTo(HashSet()) { name -> CategoryGroup.entries.firstOrNull { it.name == name } }
                    ?: defaults.types,
                distances = this[DISTANCES]
                    ?.mapNotNullTo(HashSet()) { name -> DistanceBucket.entries.firstOrNull { it.name == name } }
                    ?: defaults.distances,
                signals = this[SIGNALS]
                    ?.mapNotNullTo(HashSet()) { name -> SignalLevel.entries.firstOrNull { it.name == name } }
                    ?: defaults.signals,
                manufacturers = this[MANUFACTURERS] ?: defaults.manufacturers,
                hideGone = this[HIDE_GONE] ?: defaults.hideGone,
            ),
        )
    }

    private companion object {
        val SORT = stringPreferencesKey("sort")
        val NAMED_ONLY = booleanPreferencesKey("named_only")
        val GROUPS = stringSetPreferencesKey("groups")
        val HIDE_GONE = booleanPreferencesKey("hide_gone")
        val SHOW_OTHER = booleanPreferencesKey("show_other")
        val DISTANCES = stringSetPreferencesKey("distances")
        val SIGNALS = stringSetPreferencesKey("signals")
        val MANUFACTURERS = stringSetPreferencesKey("manufacturers")
    }
}

/** Implemented by the Application; ViewModel factories read data dependencies through it. */
interface DataDependenciesProvider {
    val listPreferences: ListPreferencesRepository
    val hiddenDevices: HiddenDevicesRepository
    val savedDevices: SavedDevicesRepository
}
