package com.gamenuri.btscanner24.feature.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gamenuri.btscanner24.core.billing.PremiumState
import com.gamenuri.btscanner24.core.billing.PremiumStore
import com.gamenuri.btscanner24.core.billing.RestoreResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val premium: PremiumState = PremiumState(),
    val showPaywall: Boolean = false,
    val isRestoring: Boolean = false,
)

/** Premium card, restore and the paywall sheet (#56). The other rows only open system screens. */
class SettingsViewModel(private val store: PremiumStore) : ViewModel() {
    private val showPaywall = MutableStateFlow(false)
    private val isRestoring = MutableStateFlow(false)
    private val _messages = Channel<RestoreResult>(Channel.BUFFERED)

    /** Results of "Restore purchase", shown once in a snackbar. */
    val messages: Flow<RestoreResult> = _messages.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> =
        combine(store.state, showPaywall, isRestoring) { premium, sheet, restoring ->
            // The sheet closes by itself once the purchase goes through.
            SettingsUiState(premium = premium, showPaywall = sheet && !premium.isPurchased, isRestoring = restoring)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState(store.state.value))

    init {
        store.refresh()
    }

    fun openPaywall() {
        // Nothing is sold in this build (#205): there is no paywall to open.
        if (!store.state.value.canBuy) return
        store.refresh()
        showPaywall.value = true
    }

    fun closePaywall() {
        showPaywall.value = false
    }

    fun buy(activity: Activity) {
        if (!store.purchase(activity)) store.refresh()
    }

    fun restore() {
        if (isRestoring.value) return
        isRestoring.value = true
        viewModelScope.launch {
            val result = try {
                store.restore()
            } finally {
                isRestoring.value = false
            }
            if (result == RestoreResult.RESTORED) showPaywall.value = false
            _messages.send(result)
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(store: PremiumStore) = viewModelFactory { initializer { SettingsViewModel(store) } }
    }
}
