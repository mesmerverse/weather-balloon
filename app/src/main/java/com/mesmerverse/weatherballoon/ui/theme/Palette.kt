package com.mesmerverse.weatherballoon.ui.theme

import androidx.compose.ui.graphics.Color
import com.mesmerverse.weatherballoon.ui.Sky

/**
 * The whole screen is tinted by what the sky is doing. One gradient, one ink
 * color, one halo accent, one rain blue — nothing else. Day palettes are lit
 * from below by a warm horizon; night palettes go deep indigo.
 */
data class SkyPalette(
    val top: Color,
    val bottom: Color,
    /** Primary text. */
    val ink: Color,
    /** Secondary text, hairlines. */
    val dim: Color,
    /** The halo gold — halo, accents, today’s range bar. */
    val halo: Color,
    /** Precipitation figures. */
    val rain: Color,
    /** Track behind the daily range bars. */
    val track: Color,
    val isDark: Boolean,
)

fun skyPalette(sky: Sky?, isDay: Boolean): SkyPalette {
    val halo = if (isDay) Color(0xFFB8791E) else Color(0xFFF2C56B)
    return if (isDay) when (sky) {
        Sky.CLEAR, null -> day(Color(0xFF7FB2E0), Color(0xFFF3E2C0), halo)
        Sky.PARTLY -> day(Color(0xFF8FB4D6), Color(0xFFE9DCC4), halo)
        Sky.OVERCAST, Sky.FOG -> day(Color(0xFFA3B0BC), Color(0xFFD9D5CB), halo)
        // The gray-blue rain sky is the low-contrast worst case: rain figures
        // and dim text both need to run darker here to stay readable (WCAG).
        Sky.RAIN, Sky.STORM -> day(
            Color(0xFF7C8DA0), Color(0xFFB4BCC0), halo,
            rain = Color(0xFF14324D),
            dimAlpha = 0.85f,
        )
        Sky.SNOW -> day(Color(0xFFAEBDCB), Color(0xFFEAEEF2), halo)
    } else when (sky) {
        Sky.CLEAR, Sky.PARTLY, null -> night(Color(0xFF0F1B31), Color(0xFF2C3A55), halo)
        Sky.OVERCAST, Sky.FOG -> night(Color(0xFF141B26), Color(0xFF2A3340), halo)
        Sky.RAIN, Sky.STORM -> night(Color(0xFF10161F), Color(0xFF232C38), halo)
        Sky.SNOW -> night(Color(0xFF1A2230), Color(0xFF39434F), halo)
    }
}

private fun day(
    top: Color,
    bottom: Color,
    halo: Color,
    rain: Color = Color(0xFF1F4E77),
    dimAlpha: Float = 0.7f,
) = SkyPalette(
    top = top,
    bottom = bottom,
    ink = Color(0xFF1C2430),
    dim = Color(0xFF1C2430).copy(alpha = dimAlpha),
    halo = halo,
    rain = rain,
    track = Color(0x2E1C2430),
    isDark = false,
)

private fun night(top: Color, bottom: Color, halo: Color) = SkyPalette(
    top = top,
    bottom = bottom,
    ink = Color(0xFFECF1F7),
    dim = Color(0xB3ECF1F7),
    halo = halo,
    rain = Color(0xFF9CC7EE),
    track = Color(0x33ECF1F7),
    isDark = true,
)
