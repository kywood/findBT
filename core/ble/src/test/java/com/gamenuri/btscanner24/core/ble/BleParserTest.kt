package com.gamenuri.btscanner24.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleParserTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `parses name, apple manufacturer and tx power`() {
        val raw = bytes(
            0x02, 0x01, 0x06,                   // flags: LE General + BR/EDR off
            0x05, 0x09, 'B'.code, 'u'.code, 'd'.code, 's'.code, // complete local name
            0x03, 0xFF, 0x4C, 0x00,             // manufacturer data, Apple (0x004C)
            0x02, 0x0A, 0xF4,                   // tx power -12 dBm
        )
        val parsed = BleParser.parse(raw)
        assertEquals("Buds", parsed.name)
        assertEquals("Apple", parsed.manufacturerId)
        assertEquals(-12, parsed.txPower)
        assertEquals("LE General, BR/EDR Off", parsed.flags)
    }

    @Test
    fun `stops at zero length padding and tolerates truncated data`() {
        val parsed = BleParser.parse(bytes(0x02, 0x0A))
        assertNull(parsed.txPower)
        assertEquals(emptyList<String>(), BleParser.parse(bytes(0x00, 0x00)).serviceUuids)
    }
}
