package com.gamenuri.btscanner24.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Background and content colour of the find screen for one proximity state. [accent] colours the
 * arrow, rings and gauge; close states keep the brand blue and turn only these mint (#77). The
 * background is a vertical gradient from [background] to [backgroundBottom] (flat when equal).
 */
@Immutable
data class FindStateColors(
    val background: Color,
    val content: Color,
    val accent: Color = content,
    val backgroundBottom: Color = background,
)

/** "Found it!" screen (#70). Its own token so the palette can change (#77) without touching layout. */
@Immutable
data class FoundColors(
    /** Vertical gradient, top to bottom. */
    val backgroundTop: Color,
    val backgroundBottom: Color,
    /** Title and summary. */
    val content: Color,
    /** The two rings around the check. */
    val rings: Color,
    val heroCircle: Color,
    val check: Color,
    val buttonContainer: Color,
    val buttonContent: Color,
)

/** Colours Material's scheme has no slot for: find-screen states and signal strength. */
@Immutable
data class NearoColors(
    val findSearching: FindStateColors,
    val findFar: FindStateColors,
    val findClose: FindStateColors,
    val findVeryClose: FindStateColors,
    val findHere: FindStateColors,
    val findLost: FindStateColors,
    val found: FoundColors,
    val signalStrong: Color,
    val signalMedium: Color,
    val signalWeak: Color,
    val signalInactive: Color,
)

// The find screen is always light-on-blue, the same in light and dark mode.
private val White = Color(0xFFFFFFFF)
private val Mint = Color(0xFF2EE6A0)

/** Find-mode blue (color-modes-v2): bright, matte and close to a pure blue. */
private val FindBlue = Color(0xFF2563FF)

/** The deep blue the find screens shade towards; bars that sit on a blue screen are tinted with it (#173). */
val FindBlueDeep = Color(0xFF0B2A8A)

/** [FindBlue] 7 % towards #0B2A8A: the screens are nearly flat, only a touch deeper at the bottom. */
private val FindBlueBottom = Color(0xFF235FF7)

/** Far and waiting: one step calmer than [FindBlue]. */
private val FindBlueFar = Color(0xFF215AEC)

private val findColors = NearoColors(
    // Find mode is brand blue all the way through (#97, color-modes-v2); browsing stays dark.
    findSearching = FindStateColors(FindBlueFar, White),
    findFar = FindStateColors(FindBlueFar, White),
    findClose = FindStateColors(FindBlue, White, backgroundBottom = FindBlueBottom),
    // Mint only marks success on the arrow, rings and gauge (#77).
    findVeryClose = FindStateColors(FindBlue, White, accent = Mint, backgroundBottom = FindBlueBottom),
    findHere = FindStateColors(FindBlue, White, accent = Mint, backgroundBottom = FindBlueBottom),
    // Signal lost: the same blue with less colour (slate), never black (#97).
    findLost = FindStateColors(Color(0xFF4A5A85), White, backgroundBottom = Color(0xFF2F3B5E)),
    // Found: the same blue as "close"; mint only on the check and rings (#77).
    found = FoundColors(
        backgroundTop = FindBlue,
        backgroundBottom = FindBlueBottom,
        content = White,
        rings = Mint,
        heroCircle = Mint,
        check = Color(0xFF00391F),
        buttonContainer = White,
        buttonContent = FindBlue,
    ),
    signalStrong = Color.Unspecified,
    signalMedium = Color.Unspecified,
    signalWeak = Color.Unspecified,
    signalInactive = Color.Unspecified,
)

internal val LightNearoColors = findColors.copy(
    signalStrong = Color(0xFF1E9E55),
    signalMedium = Color(0xFFD98E00),
    signalWeak = Color(0xFFD93F45),
    signalInactive = Color(0xFFC5C6D0),
)

internal val DarkNearoColors = findColors.copy(
    signalStrong = Color(0xFF4CD787),
    signalMedium = Color(0xFFFFB83D),
    signalWeak = Color(0xFFFF8A8D),
    signalInactive = Color(0xFF44474F),
)

internal val LocalNearoColors = staticCompositionLocalOf { LightNearoColors }
