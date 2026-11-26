package com.gamenuri.btscanner24.core.billing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoSalePremiumStoreTest {

    @Test
    fun sellsNothingAndLocksNothing() {
        val state = NoSalePremiumStore().state.value
        assertFalse(state.canBuy)
        assertFalse(state.isPremium)
        assertNull(state.formattedPrice)
        // No limit applies when nothing can be bought (#205).
        assertTrue(state.unlocked)
    }

    @Test
    fun restoreFindsNothing() = runBlocking {
        assertEquals(RestoreResult.NOTHING_TO_RESTORE, NoSalePremiumStore().restore())
    }

    @Test
    fun aStoreThatSellsStillLocksForFreeUsers() {
        assertFalse(PremiumState().unlocked)
        assertTrue(PremiumState(isPremium = true).unlocked)
    }
}
