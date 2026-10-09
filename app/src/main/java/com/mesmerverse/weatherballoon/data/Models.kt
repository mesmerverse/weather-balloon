package com.mesmerverse.weatherballoon.data

/** A place the forecast can be pinned to. Coordinates are all Open-Meteo needs. */
data class Place(
    val label: String,
    val lat: Double,
    val lon: Double,
) {
    /**
     * Real coordinates and a sane label. Search results come off the network,
     * so this gates everything persisted — a non-finite coordinate would make
     * saving it throw, and an out-of-range one would fail every refresh.
     */
    val isValid: Boolean
        get() = lat.isFinite() && lon.isFinite() &&
            lat in -90.0..90.0 && lon in -180.0..180.0 &&
            label.isNotBlank() && label.length <= MAX_LABEL_LENGTH

    companion object {
        const val MAX_LABEL_LENGTH = 200
    }
}

data class CurrentConditions(
    val tempC: Double,
    val apparentC: Double,
    val humidity: Int,
    val windKmh: Double,
    val code: Int,
    val isDay: Boolean,
)

data class HourlyItem(
    /** ISO local time at the location, e.g. "2026-07-04T14:00". */
    val time: String,
    val tempC: Double,
    val code: Int,
    val precipProbability: Int,
    val isDay: Boolean,
)

data class DailyItem(
    /** ISO date at the location, e.g. "2026-07-04". */
    val date: String,
    val code: Int,
    val minC: Double,
    val maxC: Double,
    val precipProbability: Int,
    val precipSumMm: Double,
    val windMaxKmh: Double,
    val uvMax: Double,
    val sunrise: String,
    val sunset: String,
)

data class Forecast(
    val current: CurrentConditions,
    /** The forecast location's UTC offset, for computing "now" there. */
    val utcOffsetSeconds: Int,
    /** Hourly items for all 7 days, starting at midnight today. */
    val hourly: List<HourlyItem>,
    val daily: List<DailyItem>,
)
