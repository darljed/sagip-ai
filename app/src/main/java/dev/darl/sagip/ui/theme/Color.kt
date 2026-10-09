package dev.darl.sagip.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * SAGIP design tokens — adapted from the unslop.site "AI Chat" reference.
 * Exact values mirrored from docs/DESIGN-SYSTEM.md. We keep the reference's
 * calm dark surface + soft-lavender text + single expressive accent, remapped
 * to SAGIP's emergency-assistant semantics (e.g. "Online" dot -> "Offline · Ready").
 */
object SagipColors {
    // Canvas (deep indigo -> near black gradient)
    val CanvasTop = Color(0xFF0B0A1A)
    val CanvasBottom = Color(0xFF050410)

    // Surfaces (translucent, float on the canvas)
    val Surface = Color(0x0FFFFFFF)        // rgba(255,255,255,0.06)
    val SurfaceStrong = Color(0x14FFFFFF)  // rgba(255,255,255,0.08)

    // Text
    val Text = Color(0xFFE8EAFF)           // soft lavender-white
    val TextDim = Color(0x80E8EAFF)        // 50% — meta labels, source line

    // Accent (the AI "orb")
    val Accent = Color(0xFF7C5CFF)
    val Accent2 = Color(0xFF4CC8FF)
    val AccentSoft = Color(0x2E7C5CFF)     // rgba(124,92,255,0.18)

    // Status: our core differentiator — green "Offline · Ready" dot (was "Online")
    val Ok = Color(0xFF7BE3A8)

    // Severity palette (SAGIP safety layer — not in the reference)
    val SeverityInfo = Color(0xFF8AA0C0)
    val SeverityCaution = Color(0xFFF2C94C)
    val SeverityUrgent = Color(0xFFF2994A)
    val SeverityCritical = Color(0xFFEB5757)

    val CanvasGradient = Brush.verticalGradient(listOf(CanvasTop, CanvasBottom))
    val OrbGradient = Brush.linearGradient(listOf(Accent, Accent2))
}
