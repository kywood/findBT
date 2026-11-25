package com.gamenuri.btscanner24.core.ads

/**
 * AdMob ad unit IDs (#31), as the app's build config has them: real ones in a release, Google's test
 * units in debug builds (#35). The test IDs live in the app's build script only, not in code.
 */
data class AdUnits(val banner: String, val interstitial: String, val rewarded: String)
