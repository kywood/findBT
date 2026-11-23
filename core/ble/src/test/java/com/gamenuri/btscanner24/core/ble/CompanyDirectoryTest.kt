package com.gamenuri.btscanner24.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompanyDirectoryTest {

    private val directory = TestCompanyDirectory.instance

    @Test
    fun `well known company ids resolve to short brand names`() {
        assertEquals("Apple", directory.companyName(0x004C))
        assertEquals("Samsung", directory.companyName(0x0075))
        assertEquals("Huawei", directory.companyName(0x027D))
        assertEquals("Sony", directory.companyName(0x012D))
        assertEquals("Microsoft", directory.companyName(0x0006))
        assertEquals("Nordic Semiconductor", directory.companyName(0x0059))
    }

    @Test
    fun `unassigned id returns null`() {
        assertNull(directory.companyName(0xFFFF))
    }

    @Test
    fun `legal suffixes are stripped`() {
        assertEquals("Apple", CompanyDirectory.shortName("Apple, Inc."))
        assertEquals("Xiaomi", CompanyDirectory.shortName("Xiaomi Inc."))
        assertEquals("Realtek Semiconductor", CompanyDirectory.shortName("Realtek Semiconductor Corporation"))
        assertEquals("Shenzhen Foo Technology", CompanyDirectory.shortName("Shenzhen Foo Technology Co., Ltd."))
    }
}
