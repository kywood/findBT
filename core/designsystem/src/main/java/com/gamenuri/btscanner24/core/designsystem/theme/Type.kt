package com.gamenuri.btscanner24.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val Base = Typography()

/** System font (Roboto / Noto per locale) with the token sizes and weights. */
internal val NearoTypography = Base.copy(
    headlineSmall = Base.headlineSmall.copy(fontSize = 24.sp, fontWeight = FontWeight.Medium),
    titleLarge = Base.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Medium),
    titleMedium = Base.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyMedium = Base.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = Base.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelLarge = Base.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
)

/** Styles the Material scale has no slot for. */
object NearoTextStyles {
    /** The big "~3 m" on the find screen. */
    val findDistance = TextStyle(fontSize = 54.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)

    /** "Close", "Very close" under the distance. */
    val findBand = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}
