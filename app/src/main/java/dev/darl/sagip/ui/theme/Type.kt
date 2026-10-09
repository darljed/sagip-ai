package dev.darl.sagip.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.darl.sagip.R

/**
 * One typeface only: Geist (variable). The reference pairs Geist with Instrument Serif
 * and Geist Mono; we merge them into a single family — editorial hierarchy comes from
 * weight + tight tracking on big headlines instead of a second font.
 * Scale is deliberately larger than the reference (min body 17sp, min label 13sp).
 */
@OptIn(ExperimentalTextApi::class)
val Geist = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(R.font.geist, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

object Space {
    const val xs = 4; const val sm = 8; const val md = 12; const val lg = 16
    const val xl = 24; const val xxl = 32; const val huge = 48
    /** Screen edge gutter. */
    const val gutter = 20
}

private fun t(size: Int, line: Int, weight: Int, track: Double = 0.0) = TextStyle(
    fontFamily = Geist, fontSize = size.sp, lineHeight = line.sp,
    fontWeight = FontWeight(weight), letterSpacing = track.sp,
)

val SagipTypography = Typography(
    displayLarge = t(44, 48, 700, -1.2),
    displayMedium = t(36, 40, 700, -0.9),
    headlineLarge = t(32, 38, 700, -0.8),
    headlineMedium = t(28, 34, 700, -0.6),
    headlineSmall = t(24, 30, 600, -0.4),
    titleLarge = t(22, 28, 600, -0.3),
    titleMedium = t(19, 26, 600, -0.1),
    titleSmall = t(17, 24, 600),
    bodyLarge = t(18, 28, 400),
    bodyMedium = t(17, 26, 400),
    bodySmall = t(15, 22, 400),
    labelLarge = t(16, 22, 600),
    labelMedium = t(14, 20, 500),
    labelSmall = t(13, 18, 500, 0.1),
)
