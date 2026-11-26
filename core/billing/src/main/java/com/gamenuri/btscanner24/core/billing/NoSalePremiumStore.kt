package com.gamenuri.btscanner24.core.billing

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The store of a build that sells nothing (#205): never bought, no price, never talks to Google Play.
 * [PlayPremiumStore] stays in the code for when Premium is sold.
 */
class NoSalePremiumStore : PremiumStore {
    private val sold = MutableStateFlow(PremiumState(canBuy = false))

    override val state: StateFlow<PremiumState> = sold.asStateFlow()

    override fun refresh() = Unit

    override fun purchase(activity: Activity): Boolean = false

    override suspend fun restore(): RestoreResult = RestoreResult.NOTHING_TO_RESTORE
}
