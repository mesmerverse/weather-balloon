package com.mesmerverse.weatherballoon.data

import com.mesmerverse.weatherballoon.net.httpGetJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

/**
 * Open-Meteo client (keyless, no account, no tracking headers). Everything is
 * fetched in metric and converted at display time so a unit switch never needs
 * the network. Coordinates are rounded to ~1 km before they leave the device;
 * Open-Meteo itself resolves them to the nearest model grid point, which is
 * what makes "use where I am" work with no location search at all.
 */
object OpenMeteo {

    /** Parsed forecast plus the raw payload so the last good response can be cached. */
    data class FetchResult(val forecast: Forecast, val rawJson: String)

    suspend fun fetchForecast(lat: Double, lon: Double): FetchResult = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${coordinate(lat)}&longitude=${coordinate(lon)}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m," +
            "weather_code,wind_speed_10m,is_day" +
            "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min," +
            "precipitation_probability_max,precipitation_sum,wind_speed_10m_max," +
            "uv_index_max,sunrise,sunset" +
            "&forecast_days=7&timezone=auto"
        val json = httpGetJson(url)
        FetchResult(parseForecast(json), json.toString())
    }

    fun parseForecast(json: JSONObject): Forecast {
        val current = json.getJSONObject("current").let { c ->
            CurrentConditions(
                tempC = plausible(c.getDouble("temperature_2m"), TEMP_C),
                apparentC = plausible(c.getDouble("apparent_temperature"), FEELS_LIKE_C),
                humidity = c.getInt("relative_humidity_2m").coerceIn(0, 100),
                windKmh = plausible(c.getDouble("wind_speed_10m"), WIND_KMH),
                code = c.getInt("weather_code"),
                isDay = c.getInt("is_day") == 1,
            )
        }

        val hourly = json.getJSONObject("hourly").let { h ->
            val times = h.getJSONArray("time")
            val temps = h.getJSONArray("temperature_2m")
            val codes = h.getJSONArray("weather_code")
            val precip = h.getJSONArray("precipitation_probability")
            val isDay = h.getJSONArray("is_day")
            (0 until times.length()).map { i ->
                HourlyItem(
                    time = isoDateTime(times.getString(i)),
                    tempC = plausible(temps.getDouble(i), TEMP_C),
                    code = codes.getInt(i),
                    precipProbability = precip.optInt(i, 0).coerceIn(0, 100),
                    isDay = isDay.getInt(i) == 1,
                )
            }
        }

        val daily = json.getJSONObject("daily").let { d ->
            val dates = d.getJSONArray("time")
            val codes = d.getJSONArray("weather_code")
            val maxes = d.getJSONArray("temperature_2m_max")
            val mins = d.getJSONArray("temperature_2m_min")
            val precip = d.getJSONArray("precipitation_probability_max")
            val precipSum = d.getJSONArray("precipitation_sum")
            val windMax = d.getJSONArray("wind_speed_10m_max")
            val uvMax = d.getJSONArray("uv_index_max")
            val sunrises = d.getJSONArray("sunrise")
            val sunsets = d.getJSONArray("sunset")
            (0 until dates.length()).map { i ->
                DailyItem(
                    date = isoDate(dates.getString(i)),
                    code = codes.getInt(i),
                    minC = plausible(mins.getDouble(i), TEMP_C),
                    maxC = plausible(maxes.getDouble(i), TEMP_C),
                    precipProbability = precip.optInt(i, 0).coerceIn(0, 100),
                    precipSumMm = plausible(precipSum.optDouble(i, 0.0), PRECIP_MM),
                    windMaxKmh = plausible(windMax.optDouble(i, 0.0), WIND_KMH),
                    uvMax = plausible(uvMax.optDouble(i, 0.0), UV_INDEX),
                    sunrise = isoDateTime(sunrises.getString(i)),
                    sunset = isoDateTime(sunsets.getString(i)),
                )
            }
        }

        // Structurally-valid-but-empty payloads (or a corrupt cache) must fail
        // here, where callers handle it, not crash the UI on .first()/minOf.
        require(hourly.isNotEmpty() && daily.isNotEmpty()) { "Empty forecast payload" }

        val utcOffsetSeconds = json.getInt("utc_offset_seconds")
        require(utcOffsetSeconds in -MAX_ZONE_OFFSET..MAX_ZONE_OFFSET) { "Bad UTC offset" }

        return Forecast(
            current = current,
            utcOffsetSeconds = utcOffsetSeconds,
            hourly = hourly,
            daily = daily,
        )
    }

    // A hostile-but-well-formed 200 would otherwise be saved as the "last good"
    // forecast and then crash composition on every launch until app data is
    // cleared (dates are parsed and doubles rounded at render time, unguarded).
    // parseForecast is the one choke point both the fetch and cache-load paths
    // share, and its exceptions already fall into their error handling — so
    // every value the UI consumes semantically gets validated here.

    private val MAX_ZONE_OFFSET = ZoneOffset.MAX.totalSeconds  // ±18:00

    private const val MAX_SEARCH_RESULTS = 8

    private fun finite(value: Double): Double {
        require(value.isFinite()) { "Non-finite number in payload" }
        return value
    }

    /**
     * Finite and physically plausible. Wildly out-of-range values (±1e308 °C)
     * are finite but break chart math, so they fail the payload like any
     * other bad value.
     */
    private fun plausible(value: Double, range: ClosedFloatingPointRange<Double>): Double {
        require(finite(value) in range) { "Implausible value in payload" }
        return value
    }

    private val TEMP_C = -100.0..70.0
    private val FEELS_LIKE_C = -120.0..90.0
    private val WIND_KMH = 0.0..500.0
    private val PRECIP_MM = 0.0..2_000.0
    private val UV_INDEX = 0.0..30.0

    private fun isoDate(value: String): String {
        LocalDate.parse(value)
        return value
    }

    private fun isoDateTime(value: String): String {
        LocalDateTime.parse(value)
        return value
    }

    /** City search via Open-Meteo's geocoding API. */
    suspend fun geocode(query: String): List<Place> = withContext(Dispatchers.IO) {
        val url = "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=${URLEncoder.encode(query.trim(), "UTF-8")}" +
            "&count=$MAX_SEARCH_RESULTS&language=${URLEncoder.encode(Locale.getDefault().language, "UTF-8")}" +
            "&format=json"
        val results = httpGetJson(url).optJSONArray("results") ?: return@withContext emptyList()
        // Asked for 8; never render more than that whatever comes back, and
        // drop (rather than fail the search on) any malformed entry.
        (0 until minOf(results.length(), MAX_SEARCH_RESULTS)).mapNotNull { i ->
            runCatching {
                val place = results.getJSONObject(i)
                val label = listOfNotNull(
                    place.getString("name"),
                    place.optString("admin1").takeIf { it.isNotBlank() },
                    place.optString("country_code").takeIf { it.isNotBlank() },
                ).joinToString(", ")
                Place(label, place.getDouble("latitude"), place.getDouble("longitude"))
            }.getOrNull()?.takeIf { it.isValid }
        }
    }

    // Locale-independent formatting: some locales would emit "39,74".
    private fun coordinate(value: Double): String = String.format(Locale.US, "%.2f", value)
}
