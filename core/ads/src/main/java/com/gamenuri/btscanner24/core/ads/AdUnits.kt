package com.gamenuri.btscanner24.core.ads

/** AdMob ad unit IDs (#31). The app passes Google's test IDs until the real ones are set for release (#35). */
data class AdUnits(val banner: String, val native: String, val interstitial: String, val rewarded: String) {
    companion object {
        /** Google's public sample units: always fill, never pay, safe to tap. */
        val TEST = AdUnits(
            banner = "ca-app-pub-3940256099942544/9214589741",
            native = "ca-app-pub-3940256099942544/2247696110",
            interstitial = "ca-app-pub-3940256099942544/1033173712",
            rewarded = "ca-app-pub-3940256099942544/5224354917",
        )
    }
}
