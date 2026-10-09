package com.mesmerverse.weatherballoon.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mesmerverse.weatherballoon.R

// Space Grotesk (OFL, bundled variable font — nothing fetched at runtime).
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun spaceGrotesk(weight: FontWeight) = Font(
    resId = R.font.space_grotesk,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val SpaceGrotesk = FontFamily(
    spaceGrotesk(FontWeight.Light),
    spaceGrotesk(FontWeight.Normal),
    spaceGrotesk(FontWeight.Medium),
    spaceGrotesk(FontWeight.SemiBold),
    spaceGrotesk(FontWeight.Bold),
)

val WeatherBalloonTypography = Typography(
    // The big temperature.
    displayLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontSize = 112.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-4).sp,
    ),
    // Condition line under the temperature.
    headlineSmall = TextStyle(
        fontFamily = SpaceGrotesk,
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
    ),
    // Place name in the top bar.
    titleMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    // Section labels: NEXT 24 HOURS / 7 DAYS.
    labelMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 2.sp,
    ),
    // Hourly and daily figures.
    bodyLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
    ),
    // Footer / metadata.
    bodySmall = TextStyle(
        fontFamily = SpaceGrotesk,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
    ),
)
