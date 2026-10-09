package com.mesmerverse.weatherballoon.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Material components (sheet, text fields) pick up the sky palette so nothing
 * looks bolted on. The screen itself paints its own gradient.
 */
@Composable
fun WeatherBalloonTheme(palette: SkyPalette, content: @Composable () -> Unit) {
    val scheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.halo,
            onPrimary = palette.top,
            surface = palette.top,
            onSurface = palette.ink,
            surfaceVariant = palette.bottom,
            onSurfaceVariant = palette.dim,
            surfaceContainerLow = palette.top,
            background = palette.top,
            onBackground = palette.ink,
        )
    } else {
        lightColorScheme(
            primary = palette.halo,
            onPrimary = palette.bottom,
            surface = palette.bottom,
            onSurface = palette.ink,
            surfaceVariant = palette.top,
            onSurfaceVariant = palette.dim,
            surfaceContainerLow = palette.bottom,
            background = palette.bottom,
            onBackground = palette.ink,
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = WeatherBalloonTypography,
        content = content,
    )
}
