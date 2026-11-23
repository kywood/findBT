package com.gamenuri.btscanner24.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleParserTest {

    private val companies = TestCompanyDirectory.instance

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `parses name, apple manufacturer and tx power`() {
        val raw = bytes(
            0x02, 0x01, 0x06, // flags: LE General + BR/EDR off
            0x05, 0x09, 'B'.code, 'u'.code, 'd'.code, 's'.code, // complete local name
            0x03, 0xFF, 0x4C, 0x00, // manufacturer data, Apple (0x004C)
            0x02, 0x0A, 0xF4, // tx power -12 dBm
        )
        val parsed = BleParser.parse(raw, companies)
        assertEquals("Buds", parsed.name)
        assertEquals("Apple", parsed.manufacturer)
        assertEquals(-12, parsed.txPower)
        assertEquals("LE General, BR/EDR Off", parsed.flags)
    }

    @Test
    fun `stops at zero length padding and tolerates truncated data`() {
        val parsed = BleParser.parse(bytes(0x02, 0x0A), companies)
        assertNull(parsed.txPower)
        assertEquals(emptyList<String>(), BleParser.parse(bytes(0x00, 0x00), companies).serviceUuids)
    }

    @Test
    fun `manufacturer comes from the official SIG table, not a short hand-made list`() {
        // 0x0171 was labelled Huawei in the old table; the SIG assigns it to Amazon.
        val amazon = BleParser.parse(bytes(0x03, 0xFF, 0x71, 0x01), companies)
        assertEquals("Amazon", amazon.manufacturer)
        assertEquals(0x0171, amazon.companyId)
        // 0x009E Bose was missing from the old table.
        assertEquals("Bose", BleParser.parse(bytes(0x03, 0xFF, 0x9E, 0x00), companies).manufacturer)
    }

    @Test
    fun `member service UUID is used when there is no manufacturer data`() {
        // 16-bit service UUID list with 0xFE9F (Google)
        val parsed = BleParser.parse(bytes(0x03, 0x03, 0x9F, 0xFE), companies)
        assertEquals("Google", parsed.manufacturer)
        assertNull(parsed.companyId)
    }

    @Test
    fun `unassigned company id keeps the raw id but no name`() {
        val parsed = BleParser.parse(bytes(0x03, 0xFF, 0xFE, 0xFF), companies)
        assertNull(parsed.manufacturer)
        assertEquals(0xFFFE, parsed.companyId)
    }
}
