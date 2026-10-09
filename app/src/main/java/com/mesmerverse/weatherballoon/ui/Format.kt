package com.mesmerverse.weatherballoon.ui

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

fun displayTemp(celsius: Double, fahrenheit: Boolean): Int =
    (if (fahrenheit) celsius * 9 / 5 + 32 else celsius).roundToInt()

fun displayWind(kmh: Double, imperial: Boolean): String =
    if (imperial) "${(kmh * 0.621371).roundToInt()} mph" else "${kmh.roundToInt()} km/h"

fun displayPrecip(mm: Double, imperial: Boolean): String = when {
    imperial -> "%.1f in".format(mm / 25.4)
    mm < 1 -> "<1 mm"
    else -> "${mm.roundToInt()} mm"
}

/** "2026-07-04T14:00" → "2 PM" / "14:00" depending on the device clock format. */
fun hourLabel(isoLocal: String, is24Hour: Boolean): String {
    val time = LocalDateTime.parse(isoLocal)
    return if (is24Hour) {
        // A bare "14" reads as just another number above the temperature.
        "%02d:00".format(time.hour)
    } else {
        val hour = ((time.hour + 11) % 12) + 1
        "$hour${if (time.hour < 12) " AM" else " PM"}"
    }
}

/** "2026-07-04T05:34" → "05:34" / "5:34 AM". */
fun clockLabel(isoLocal: String, is24Hour: Boolean): String {
    val time = LocalDateTime.parse(isoLocal)
    return if (is24Hour) {
        "%02d:%02d".format(time.hour, time.minute)
    } else {
        val hour = ((time.hour + 11) % 12) + 1
        "%d:%02d %s".format(hour, time.minute, if (time.hour < 12) "AM" else "PM")
    }
}

/** "2026-07-04" → "Today" / "Sat" (in the device locale). */
fun dayLabel(isoDate: String, todayIso: String): String {
    if (isoDate == todayIso) return "Today"
    return LocalDate.parse(isoDate).dayOfWeek
        .getDisplayName(TextStyle.SHORT, Locale.getDefault())
}

/** Epoch millis → "12:04" / "12:04 PM" in the device zone. */
fun updatedLabel(epochMillis: Long, is24Hour: Boolean): String {
    val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    return if (is24Hour) {
        "%02d:%02d".format(time.hour, time.minute)
    } else {
        val hour = ((time.hour + 11) % 12) + 1
        "%d:%02d %s".format(hour, time.minute, if (time.hour < 12) "AM" else "PM")
    }
}
