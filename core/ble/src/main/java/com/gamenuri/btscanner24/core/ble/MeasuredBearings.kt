package com.gamenuri.btscanner24.core.ble

import com.gamenuri.btscanner24.core.model.MeasuredBearing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The directions the find screen measured, by device address, for the map to show (#174). Features
 * do not know each other: the find screen writes here, the Nearby map reads. In memory only.
 */
class MeasuredBearings {
    private val _bearings = MutableStateFlow<Map<String, MeasuredBearing>>(emptyMap())
    val bearings: StateFlow<Map<String, MeasuredBearing>> = _bearings.asStateFlow()

    /** Keeps [bearing] for [address] and drops what is no longer fresh at its time. */
    fun record(address: String, bearing: MeasuredBearing) {
        _bearings.update { all ->
            if (all[address] == bearing) {
                all
            } else {
                all.filterValues { it.isFresh(bearing.measuredAt) } + (address to bearing)
            }
        }
    }
}
