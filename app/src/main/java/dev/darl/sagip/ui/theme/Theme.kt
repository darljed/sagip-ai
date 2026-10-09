package dev.darl.sagip.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = lightColorScheme(
    primary = SagipColors.Ink, onPrimary = Color.White,
    secondary = SagipColors.Acid, onSecondary = SagipColors.Ink,
    tertiary = SagipColors.Blue,
    background = SagipColors.Paper, onBackground = SagipColors.Ink,
    surface = SagipColors.Card, onSurface = SagipColors.Ink,
    surfaceVariant = SagipColors.SurfaceStrong, onSurfaceVariant = SagipColors.Muted,
    outline = SagipColors.Line, error = SagipColors.SeverityCritical,
)

/** Light only: the Photo Journal direction is a paper canvas, even in dim rooms. */
@Composable
fun SagipTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val ignored = isSystemInDarkTheme()
    MaterialTheme(colorScheme = Scheme, typography = SagipTypography, content = content)
}
