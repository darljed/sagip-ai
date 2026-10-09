package dev.darl.sagip.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

enum class ThemeMode(val key: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** Is the UI dark for this mode? (SYSTEM follows the phone.) */
@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun SagipTheme(mode: ThemeMode = ThemeMode.LIGHT, content: @Composable () -> Unit) {
    val dark = isDark(mode)
    val p = if (dark) DarkPalette else LightPalette
    // Written before any child reads it, so the first frame already uses the right palette.
    if (SagipColors.palette !== p) SagipColors.palette = p
    val scheme = remember(dark) {
        val base = if (dark) darkColorScheme() else lightColorScheme()
        base.copy(
            primary = p.ink, onPrimary = p.onInk, secondary = SagipColors.Acid, onSecondary = SagipColors.OnAcid,
            tertiary = p.blue, background = p.paper, onBackground = p.ink, surface = p.card, onSurface = p.ink,
            surfaceVariant = p.surfaceStrong, onSurfaceVariant = p.muted, outline = p.line, error = p.sevCritical,
        )
    }
    // Default text/icon colour must follow the palette (Typography no longer bakes one in).
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides p.ink) {
        MaterialTheme(colorScheme = scheme, typography = SagipTypography, content = content)
    }
}
