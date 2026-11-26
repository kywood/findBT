package com.gamenuri.btscanner24.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceUnitTest {

    @Test
    fun `feet by default in the US, UK, Liberia and Myanmar`() {
        listOf("US", "GB", "LR", "MM", "us").forEach { assertEquals(it, DistanceUnit.FEET, DistanceUnit.forRegion(it)) }
    }

    @Test
    fun `metres everywhere else, and when the region is not known`() {
        listOf("KR", "JP", "DE", "BR", "ES", "CA", "").forEach {
            assertEquals(it, DistanceUnit.METERS, DistanceUnit.forRegion(it))
        }
    }
}
