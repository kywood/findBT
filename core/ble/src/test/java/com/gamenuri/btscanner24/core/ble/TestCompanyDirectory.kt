package com.gamenuri.btscanner24.core.ble

import java.io.File

/** Reads the real bundled tables; JVM unit tests run with the module directory as working dir. */
object TestCompanyDirectory {
    val instance = CompanyDirectory { name -> File("src/main/assets", name).inputStream() }
}
