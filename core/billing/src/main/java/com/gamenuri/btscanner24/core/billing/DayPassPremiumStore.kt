package com.gamenuri.btscanner24.core.billing

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Adds the rewarded ad's day pass (#81) to a [PremiumStore]: while it runs, [PremiumState.isPremium]
 * is true, so ads go away and Premium features unlock; when it ends, everything quietly goes back.
 */
class DayPassPremiumStore(
    private val base: PremiumStore,
    private val pass: DayPass,
    private val clock: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : PremiumStore {

    constructor(context: Context, base: PremiumStore) : this(
        base,
        DayPass(
            DayPass.SharedPreferencesStorage(
                context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
            ),
        ),
    )

    /** Bumped when a pass starts or ends, or the day may have changed, to recompute [state]. */
    private val tick = MutableStateFlow(0)
    private var expiry: Job? = null

    override val state: StateFlow<PremiumState> = combine(base.state, tick) { bought, _ -> withPass(bought) }
        .stateIn(scope, SharingStarted.Eagerly, withPass(base.state.value))

    init {
        scheduleExpiry()
    }

    private fun withPass(bought: PremiumState): PremiumState {
        val now = clock()
        val until = pass.until(now)
        return bought.copy(
            isPremium = bought.isPremium || until != null,
            isPurchased = bought.isPremium,
            dayPassUntil = until,
            dayPassAvailable = !bought.isPremium && pass.isAvailable(now),
        )
    }

    override fun refresh() {
        base.refresh()
        tick.value++
    }

    override fun purchase(activity: Activity): Boolean = base.purchase(activity)

    override suspend fun restore(): RestoreResult = base.restore()

    override fun grantDayPass(): Long? {
        if (base.state.value.isPremium) return null
        val until = pass.grant(clock()) ?: return null
        tick.value++
        scheduleExpiry()
        return until
    }

    private fun scheduleExpiry() {
        expiry?.cancel()
        val until = pass.until(clock()) ?: return
        expiry = scope.launch {
            delay(until - clock())
            tick.value++
        }
    }

    private companion object {
        const val PREFS = "day_pass"
    }
}
