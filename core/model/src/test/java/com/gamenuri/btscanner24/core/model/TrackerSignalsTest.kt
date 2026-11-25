package com.gamenuri.btscanner24.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerSignalsTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun matches(
        maker: Map<Int, ByteArray> = emptyMap(),
        data: Map<Int, ByteArray> = emptyMap(),
        services: List<Int> = emptyList(),
    ) = TrackerSignals.matches(maker::get, data::get, services)

    @Test
    fun `a Find My tag away from its owner is a tracker, one near its owner is not`() {
        assertTrue(matches(maker = mapOf(0x004C to bytes(0x12, 0x19, 0x10, 0xAA, 0xBB))))
        assertFalse(matches(maker = mapOf(0x004C to bytes(0x12, 0x02, 0x00, 0x01))))
    }

    @Test
    fun `other Apple advertisements are not trackers`() {
        assertFalse(matches(maker = mapOf(0x004C to bytes(0x10, 0x05, 0x01))))
        assertFalse(matches(maker = mapOf(0x004C to bytes(0x12))))
        assertFalse(matches(maker = mapOf(0x0075 to bytes(0x12, 0x19))))
    }

    @Test
    fun `a SmartTag counts only in its offline states`() {
        assertTrue(matches(data = mapOf(0xFD5A to bytes(0x12, 0x00))))
        assertTrue(matches(data = mapOf(0xFD5A to bytes(0x13))))
        assertFalse(matches(data = mapOf(0xFD5A to bytes(0x15, 0x00))))
        assertFalse(matches(data = mapOf(0xFD5A to bytes())))
    }

    @Test
    fun `a Find Hub tag counts only in unwanted-tracking mode`() {
        assertTrue(matches(data = mapOf(0xFEAA to bytes(0x41, 0x01))))
        assertFalse(matches(data = mapOf(0xFEAA to bytes(0x40, 0x01))))
        assertFalse(matches(data = mapOf(0xFEAA to bytes(0x10, 0x00))))
    }

    @Test
    fun `Tile and Chipolo are known by their service`() {
        assertTrue(matches(services = listOf(0x180F, 0xFEED)))
        assertTrue(matches(services = listOf(0xFE33)))
        assertFalse(matches(services = listOf(0x180F, 0x1802)))
        assertFalse(matches())
    }
}
