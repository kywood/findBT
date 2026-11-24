package com.gamenuri.btscanner24.core.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/** What the UI needs to know about the one-time "Nearo Premium" unlock (#32). */
data class PremiumState(
    val isPremium: Boolean = false,
    /** Google Play's localized price, e.g. "$4.99" or "4,99 €"; null until loaded. Never hard-coded. */
    val formattedPrice: String? = null,
    /** A purchase is waiting for payment (cash, bank transfer); premium turns on when it completes. */
    val isPending: Boolean = false,
)

enum class RestoreResult { RESTORED, NOTHING_TO_RESTORE, FAILED }

/** Lifetime unlock. One instance lives in the Application ([Provider]); UI reads [state]. */
interface PremiumStore {
    val state: StateFlow<PremiumState>

    /** Connects to Google Play (if needed) and refreshes price and ownership. Safe to call often. */
    fun refresh()

    /** Opens Google Play's purchase sheet. False when the product could not be loaded yet. */
    fun purchase(activity: Activity): Boolean

    /** "Restore purchase": asks Google Play again for purchases owned by this Google account. */
    suspend fun restore(): RestoreResult

    interface Provider {
        val premium: PremiumStore
    }
}
