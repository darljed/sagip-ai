package dev.darl.sagip.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * SAGIP design tokens — "Photo Journal" direction (unslop.site Mobile Apps / Core Apps).
 * Reference values: paper #F4F3EE, ink #151513, muted #6D6D66, line #D7D6CF, acid #D6FF3F,
 * blue #3458F4, plus the reference app's coral (#FF5E6C) for the single loud action.
 *
 * Merged with the earlier dark "AI Chat" system into ONE light system: warm paper canvas,
 * white photo cards with hairline borders, ink pills, and a single loud accent (coral = SOS).
 * Legacy names (Text, Accent, Surface…) are kept so un-migrated screens recolour for free.
 */
object SagipColors {
    // Photo Journal core
    val Paper = Color(0xFFF4F3EE)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF151513)
    val Muted = Color(0xFF6D6D66)
    val Line = Color(0xFFD7D6CF)
    val Acid = Color(0xFFD6FF3F)
    val Blue = Color(0xFF3458F4)
    val Coral = Color(0xFFFF5E6C)
    val CoralDeep = Color(0xFFE5384A)

    // Legacy aliases (wizard / success screens)
    val CanvasTop = Paper
    val CanvasBottom = Paper
    val Surface = Card
    val SurfaceStrong = Color(0xFFEDEBE3)
    val Text = Ink
    val TextDim = Muted
    val Accent = Ink
    val Accent2 = Acid
    val AccentSoft = Color(0x33D6FF3F)
    val Ok = Color(0xFF1F9D63)

    // Severity (safety layer) — tuned for light backgrounds
    val SeverityInfo = Blue
    val SeverityCaution = Color(0xFFB7791F)
    val SeverityUrgent = Color(0xFFE8590C)
    val SeverityCritical = Color(0xFFD92D3A)

    val CanvasGradient = Brush.verticalGradient(listOf(Paper, Paper))
    val OrbGradient = Brush.linearGradient(listOf(Ink, Ink))
}
