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

private val findColors = NearoColors(
    // Find mode is muted brand blue all the way through (#97, color-modes-v1); browsing stays dark.
    findSearching = FindStateColors(Color(0xFF365CC4), White, backgroundBottom = Color(0xFF24418F)),
    findFar = FindStateColors(Color(0xFF2E54B6), White),
    findClose = FindStateColors(Color(0xFF4777E2), White),
    // Mint only marks success on the arrow, rings and gauge (#77).
    findVeryClose = FindStateColors(Color(0xFF4777E2), White, accent = Mint),
    findHere = FindStateColors(Color(0xFF4777E2), White, accent = Mint),
    // Signal lost: the same blue with less colour (slate), never black (#97).
    findLost = FindStateColors(Color(0xFF4A5A85), White, backgroundBottom = Color(0xFF2F3B5E)),
    // Design found-blue-v2 (#77) with the muted tokens (#90 v2): mint only on the check and rings.
    found = FoundColors(
        backgroundTop = Color(0xFF4777E2),
        backgroundBottom = Color(0xFF2A4A9E),
        content = White,
        rings = Mint,
        heroCircle = Mint,
        check = Color(0xFF00391F),
        buttonContainer = White,
        buttonContent = Color(0xFF2A4A9E),
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
