package com.mesmerverse.weatherballoon.ui

/** Broad sky character, used to tint the whole screen. */
enum class Sky { CLEAR, PARTLY, OVERCAST, FOG, RAIN, SNOW, STORM }

fun sky(code: Int): Sky = when (code) {
    0, 1 -> Sky.CLEAR
    2 -> Sky.PARTLY
    3 -> Sky.OVERCAST
    45, 48 -> Sky.FOG
    in 51..67, in 80..82 -> Sky.RAIN
    in 71..77, 85, 86 -> Sky.SNOW
    in 95..99 -> Sky.STORM
    else -> Sky.PARTLY
}

/** WMO weather interpretation codes → plain words. */
fun wmoLabel(code: Int): String = when (code) {
    0 -> "Clear sky"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61 -> "Light rain"
    63 -> "Rain"
    65 -> "Heavy rain"
    66, 67 -> "Freezing rain"
    71 -> "Light snow"
    73 -> "Snow"
    75 -> "Heavy snow"
    77 -> "Snow grains"
    80, 81 -> "Rain showers"
    82 -> "Heavy showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunderstorm with hail"
    else -> "—"
}

/**
 * Something is falling at the place right now (drizzle, rain, freezing
 * rain, snow, showers, storms). Radar color doesn't say which, so the radar
 * card names it from this.
 */
fun precipitating(code: Int): Boolean =
    code in 51..67 || code in 71..77 || code in 80..86 || code in 95..99

fun wmoEmoji(code: Int, isDay: Boolean): String = when (code) {
    0, 1 -> if (isDay) "☀️" else "🌙"
    2 -> if (isDay) "⛅" else "☁️"
    3 -> "☁️"
    45, 48 -> "🌫️"
    in 51..57 -> "🌦️"
    in 61..67, in 80..82 -> "🌧️"
    in 71..77, 85, 86 -> "🌨️"
    in 95..99 -> "⛈️"
    else -> "•"
}
