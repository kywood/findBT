package com.gamenuri.btscanner24.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
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

/**
 * Japanese breaks lines between any two characters by default, which splits katakana words
 * ("ト / ラッカー"). Phrase breaking keeps words together on Android 13+ (#75); older versions rely on
 * word joiners inside the few long strings.
 */
private val JapaneseLineBreak = LineBreak(
    strategy = LineBreak.Strategy.HighQuality,
    strictness = LineBreak.Strictness.Strict,
    wordBreak = LineBreak.WordBreak.Phrase,
)

private val JapaneseTypography = NearoTypography.run {
    fun TextStyle.phrases() = copy(lineBreak = JapaneseLineBreak)
    copy(
        displayLarge = displayLarge.phrases(),
        displayMedium = displayMedium.phrases(),
        displaySmall = displaySmall.phrases(),
        headlineLarge = headlineLarge.phrases(),
        headlineMedium = headlineMedium.phrases(),
        headlineSmall = headlineSmall.phrases(),
        titleLarge = titleLarge.phrases(),
        titleMedium = titleMedium.phrases(),
        titleSmall = titleSmall.phrases(),
        bodyLarge = bodyLarge.phrases(),
        bodyMedium = bodyMedium.phrases(),
        bodySmall = bodySmall.phrases(),
        labelLarge = labelLarge.phrases(),
        labelMedium = labelMedium.phrases(),
        labelSmall = labelSmall.phrases(),
    )
}

/** [NearoTypography], with phrase line breaking for Japanese. */
internal fun nearoTypography(language: String): Typography =
    if (language == "ja") JapaneseTypography else NearoTypography

/** Styles the Material scale has no slot for. */
object NearoTextStyles {
    /** The big "~3 m" on the find screen. */
    val findDistance = TextStyle(fontSize = 54.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)

    /** "Close", "Very close" under the distance. */
    val findBand = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}
