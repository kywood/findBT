package com.gamenuri.btscanner24.core.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/** What the UI needs to know about the one-time "Nearo Premium" unlock (#32). */
data class PremiumState(
    /** Every Premium feature is on: bought, or a 24 h day pass from a rewarded ad (#81). */
    val isPremium: Boolean = false,
    /** Google Play's localized price, e.g. "$4.99" or "4,99 €"; null until loaded. Never hard-coded. */
    val formattedPrice: String? = null,
    /** A purchase is waiting for payment (cash, bank transfer); premium turns on when it completes. */
    val isPending: Boolean = false,
    /** The lifetime unlock was bought; false while only a day pass is running. */
    val isPurchased: Boolean = isPremium,
    /** End of the running day pass (epoch ms), or null. */
    val dayPassUntil: Long? = null,
    /** A rewarded ad may grant a day pass now: not bought, none running, none used today. */
    val dayPassAvailable: Boolean = false,
    /**
     * Premium can be bought in this build. Off in 2.0 (#205): no merchant account yet, so nothing is
     * sold, every free limit is lifted and the rewarded ad only buys 24 hours without ads.
     */
    val canBuy: Boolean = true,
) {
    /** Every limit is off: Premium was bought or a day pass runs, or nothing is sold so nothing is locked. */
    val unlocked: Boolean get() = isPremium || !canBuy
}

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

    /**
     * Starts a 24 h day pass after a rewarded ad was watched to the end (#81). Returns its end
     * (epoch ms), or null when none may start now ([PremiumState.dayPassAvailable] is false).
     */
    fun grantDayPass(): Long? = null

    interface Provider {
        val premium: PremiumStore
    }
}
