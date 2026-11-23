package com.gamenuri.btscanner24.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Background and content colour of the find screen for one proximity state. */
@Immutable
data class FindStateColors(val background: Color, val content: Color)

/** Colours Material's scheme has no slot for: find-screen states and signal strength. */
@Immutable
data class NearoColors(
    val findSearching: FindStateColors,
    val findFar: FindStateColors,
    val findClose: FindStateColors,
    val findVeryClose: FindStateColors,
    val findHere: FindStateColors,
    val findLost: FindStateColors,
    val signalStrong: Color,
    val signalMedium: Color,
    val signalWeak: Color,
    val signalInactive: Color,
)

// The find screen is always dark-on-colour, the same in light and dark mode.
private val findColors = NearoColors(
    findSearching = FindStateColors(Color(0xFF111318), Color(0xFFB2C5FF)),
    findFar = FindStateColors(Color(0xFF1747C2), Color(0xFFFFFFFF)),
    findClose = FindStateColors(Color(0xFF2F6BF0), Color(0xFFFFFFFF)),
    findVeryClose = FindStateColors(Color(0xFF00A88A), Color(0xFFFFFFFF)),
    findHere = FindStateColors(Color(0xFF2EE6A0), Color(0xFF00391F)),
    findLost = FindStateColors(Color(0xFF2A2D34), Color(0xFFC5C6D0)),
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
