package com.gamenuri.btscanner24


object ManufacturerResolver {

    fun resolveByManufacturerId(id: Int): String {
        return when (id) {
            0x004C -> "Apple"
            0x0075 -> "Samsung"
            0x0006 -> "Microsoft"
            0x00E0 -> "Google"
            0x038F -> "Xiaomi"
            0x0171 -> "Huawei"
            0x0078 -> "Bose"
            0x008A -> "Jabra (GN Audio)"
            0x00D7 -> "Sony"
            0x0310 -> "JBL / Harman"
            0x01D6 -> "Sennheiser"
            0x01DD -> "Philips"
            0x071E -> "QCY (Hele Electronics)"
            0x0BDA -> "Realtek"
            0x0059 -> "Nordic Semiconductor"
            0x0499 -> "Ruuvi Innovations"
            0x000F -> "Broadcom"
            0x0046 -> "MediaTek"
            0x0025 -> "Qualcomm"
            0x0157 -> "Logitech"
            0x01FF -> "LG Electronics"
            0x0117 -> "Lenovo"
            0x02FE -> "OPPO"
            0x07D8 -> "OnePlus"
            else -> "${Constants.UNKNOWN} (0x${"%04X".format(id)})"
        }
    }

    fun resolveByServiceUuid(uuid: Int): String {
        return when (uuid) {
            0xFEAA -> "Google (Eddystone Beacon)"
            0xFE9F, 0xFEA0, 0xFEF4 -> "Google"
            0xFEAF, 0xFEB0 -> "Nest Labs (Google Smart Home)"
            0xFE95 -> "Xiaomi"
            0xFEE0 -> "Anhui Huami (Amazfit / Mi Band)"
            0xFD69 -> "Samsung Electronics"
            0xFEB3 -> "Taobao (Alibaba)"
            0xFE96, 0xFE97 -> "Tesla Motors"
            0xFE03 -> "Amazon"
            0xFEFA, 0xFEF9 -> "PayPal"
            0xFEB2 -> "Microsoft"
            0xFCD2 -> "BTHome"
            0xFE9A -> "Estimote (Beacon)"
            0xFE24 -> "August Home (Smart Lock)"
            0xFE98, 0xFE99 -> "Currant (Smart Outlet)"
            else -> Constants.UNKNOWN
        }
    }
}