package com.gamenuri.btscanner24.core.ble

import android.content.Context
import java.io.InputStream

/**
 * Looks up company names from the official Bluetooth SIG Assigned Numbers, bundled as assets
 * (`bt_company_ids.tsv`, `bt_member_uuids.tsv`; regenerate with scripts/update_bt_assigned_numbers.py).
 *
 * Tables load lazily on first lookup, which happens on the scan callback thread, not the main thread.
 */
class CompanyDirectory(private val openAsset: (String) -> InputStream) {

    constructor(context: Context) : this(context.applicationContext.assets::open)

    private val companies: Map<Int, String> by lazy { load(COMPANY_FILE) }
    private val memberServices: Map<Int, String> by lazy { load(MEMBER_UUID_FILE) }

    /** Company for a manufacturer-specific data ID (AD type 0xFF), or null if not assigned. */
    fun companyName(companyId: Int): String? = companies[companyId]?.let { displayName(companyId, it) }

    /** Company that owns a 16-bit member service UUID (e.g. 0xFE9F = Google), or null. */
    fun memberServiceOwner(uuid16: Int): String? = memberServices[uuid16]?.let(::shortName)

    private fun load(file: String): Map<Int, String> =
        openAsset(file).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val tab = line.indexOf('\t')
                    if (tab <= 0) return@mapNotNull null
                    line.substring(0, tab).toIntOrNull(16)?.let { it to line.substring(tab + 1).trim() }
                }
                .toMap()
        }

    companion object {
        private const val COMPANY_FILE = "bt_company_ids.tsv"
        private const val MEMBER_UUID_FILE = "bt_member_uuids.tsv"

        /** Consumer-facing names for brands whose registered legal name reads poorly in a list. */
        private val BRAND_NAMES = mapOf(
            0x0057 to "Harman (JBL)",
            0x075A to "Harman (JBL)",
            0x0075 to "Samsung",
            0x027D to "Huawei",
            0x079A to "OPPO",
            0x072F to "OnePlus",
            0x00C4 to "LG",
            0x0171 to "Amazon",
            0x0157 to "Huami (Amazfit)",
            0x0067 to "GN (Jabra)",
            0x0089 to "GN (ReSound)",
            0x01DD to "Philips",
        )

        private val LEGAL_SUFFIX = Regex(
            "[,.]?\\s+(" +
                "Inc|Incorporated|Corp|Corporation|Co|Company|Ltd|Limited|LLC|L\\.L\\.C|GmbH|AG|SA|S\\.A|AB|ASA|" +
                "A/S|B\\.V|N\\.V|Oy|Pte|PLC|S\\.r\\.l|SAS|K\\.K" +
                ")\\.?$",
            RegexOption.IGNORE_CASE,
        )

        fun displayName(companyId: Int, registeredName: String): String =
            BRAND_NAMES[companyId] ?: shortName(registeredName)

        /** "Apple, Inc." -> "Apple", "Sony Corporation" -> "Sony", "Xiaomi Inc." -> "Xiaomi". */
        fun shortName(registeredName: String): String {
            var name = registeredName.trim()
            while (true) {
                val stripped = name.replace(LEGAL_SUFFIX, "").trim().trimEnd(',', '.')
                if (stripped == name || stripped.isEmpty()) break
                name = stripped
            }
            return name.ifEmpty { registeredName.trim() }
        }
    }
}
