package com.elprofeta.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = SealRed,
    onPrimary = ParchmentLight,
    secondary = GoldAccent,
    background = ParchmentLight,
    onBackground = InkDark,
    surface = ParchmentSurface,
    onSurface = InkDark,
    onSurfaceVariant = InkMedium,
    outline = InkMedium,
)

private val DarkColors = darkColorScheme(
    primary = GoldAccent,
    onPrimary = NightPaper,
    secondary = SealRed,
    background = NightPaper,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    onSurfaceVariant = NightInkMuted,
    outline = NightInkMuted,
)

/** Tema de la app: papel sepia de dia, pergamino oscuro de noche. */
@Composable
fun ProfetaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ProfetaTypography,
        content = content,
    )
}
