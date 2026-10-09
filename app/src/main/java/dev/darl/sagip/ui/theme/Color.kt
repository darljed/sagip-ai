package dev.darl.sagip.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * SAGIP design tokens — "Photo Journal" (unslop.site Mobile Apps / Core Apps), light + dark.
 * Reference light values: paper #F4F3EE, ink #151513, muted #6D6D66, line #D7D6CF, acid #D6FF3F,
 * blue #3458F4, coral #FF5E6C. Dark flips paper/ink and keeps acid + coral.
 *
 * Tokens are read through [SagipColors] getters backed by Compose state, so flipping the
 * palette (see [SagipTheme]) recomposes every screen with no per-screen changes.
 *
 * Ink = primary text AND the fill of "ink" pills/buttons. Content drawn ON an ink fill uses
 * [SagipColors.OnInk] / [SagipColors.InkAccent] so both themes keep contrast.
 */
class Palette(
    val dark: Boolean,
    val paper: Color, val card: Color, val ink: Color, val onInk: Color, val inkAccent: Color,
    val muted: Color, val line: Color, val surfaceStrong: Color, val accentSoft: Color,
    val blue: Color, val ok: Color,
    val sevCaution: Color, val sevUrgent: Color, val sevCritical: Color,
)

val LightPalette = Palette(
    dark = false,
    paper = Color(0xFFF4F3EE), card = Color(0xFFFFFFFF), ink = Color(0xFF151513), onInk = Color.White,
    inkAccent = Color(0xFFD6FF3F), muted = Color(0xFF6D6D66), line = Color(0xFFD7D6CF),
    surfaceStrong = Color(0xFFEDEBE3), accentSoft = Color(0x33D6FF3F), blue = Color(0xFF3458F4), ok = Color(0xFF1F9D63),
    sevCaution = Color(0xFFB7791F), sevUrgent = Color(0xFFE8590C), sevCritical = Color(0xFFD92D3A),
)

val DarkPalette = Palette(
    dark = true,
    paper = Color(0xFF11110F), card = Color(0xFF1C1C19), ink = Color(0xFFF4F3EE), onInk = Color(0xFF151513),
    inkAccent = Color(0xFF151513), muted = Color(0xFFA3A29A), line = Color(0xFF34342F),
    surfaceStrong = Color(0xFF2A2A25), accentSoft = Color(0x33D6FF3F), blue = Color(0xFF8CA0FF), ok = Color(0xFF3DD68C),
    sevCaution = Color(0xFFE0A23A), sevUrgent = Color(0xFFFF8A4C), sevCritical = Color(0xFFFF5C66),
)

object SagipColors {
    var palette by mutableStateOf(LightPalette)

    val isDark: Boolean get() = palette.dark

    val Paper: Color get() = palette.paper
    val Card: Color get() = palette.card
    val Ink: Color get() = palette.ink
    val OnInk: Color get() = palette.onInk
    val InkAccent: Color get() = palette.inkAccent
    val Muted: Color get() = palette.muted
    val Line: Color get() = palette.line
    val Blue: Color get() = palette.blue

    // Fixed brand colours (same in both themes)
    val Acid = Color(0xFFD6FF3F)
    val OnAcid = Color(0xFF151513)
    val Coral = Color(0xFFFF5E6C)
    val CoralDeep = Color(0xFFE5384A)

    // Legacy aliases (wizard / success screens)
    val CanvasTop: Color get() = Paper
    val CanvasBottom: Color get() = Paper
    val Surface: Color get() = Card
    val SurfaceStrong: Color get() = palette.surfaceStrong
    val Text: Color get() = Ink
    val TextDim: Color get() = Muted
    val Accent: Color get() = Ink
    val Accent2: Color get() = Acid
    val AccentSoft: Color get() = palette.accentSoft
    val Ok: Color get() = palette.ok

    val SeverityInfo: Color get() = palette.blue
    val SeverityCaution: Color get() = palette.sevCaution
    val SeverityUrgent: Color get() = palette.sevUrgent
    val SeverityCritical: Color get() = palette.sevCritical

    val CanvasGradient: Brush get() = Brush.verticalGradient(listOf(Paper, Paper))
    val OrbGradient: Brush get() = Brush.linearGradient(listOf(Ink, Ink))
}
