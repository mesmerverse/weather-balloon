package com.mesmerverse.weatherballoon.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

// A corrupt preferences file otherwise throws out of the settings flow at
// startup and crashes the app on every launch; settings and the cached
// forecast are all recoverable, so start over instead.
val Context.dataStore by preferencesDataStore(
    name = "weatherballoon",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

data class Settings(
    /** null = follow the device location; otherwise pinned to this place. */
    val pinnedPlace: Place? = null,
    /** null = decide by locale; true = Fahrenheit, false = Celsius. */
    val fahrenheit: Boolean? = null,
    val recentPlaces: List<Place> = emptyList(),
)

data class CachedForecast(
    val forecast: Forecast,
    val placeLabel: String,
    val fetchedAt: Long,
)

/** Settings + last-good-forecast persistence. Everything stays on the device. */
class WeatherStore(private val context: Context) {

    private val pinnedLabelKey = stringPreferencesKey("pinned_label")
    private val pinnedLatKey = doublePreferencesKey("pinned_lat")
    private val pinnedLonKey = doublePreferencesKey("pinned_lon")
    private val fahrenheitKey = booleanPreferencesKey("fahrenheit")
    private val recentsKey = stringPreferencesKey("recent_places")
    private val cacheJsonKey = stringPreferencesKey("cache_json")
    private val cacheLabelKey = stringPreferencesKey("cache_label")
    private val cacheAtKey = longPreferencesKey("cache_at")

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        val lat = prefs[pinnedLatKey]
        val lon = prefs[pinnedLonKey]
        val label = prefs[pinnedLabelKey]
        Settings(
            pinnedPlace = if (label != null && lat != null && lon != null) {
                Place(label, lat, lon)
            } else null,
            fahrenheit = prefs[fahrenheitKey],
            recentPlaces = prefs[recentsKey]?.let(::parsePlaces) ?: emptyList(),
        )
    }.distinctUntilChanged()

    /** Pin the forecast to a searched place and remember it in recents. */
    suspend fun pinPlace(place: Place) {
        if (!place.isValid) return
        context.dataStore.edit { prefs ->
            prefs[pinnedLabelKey] = place.label
            prefs[pinnedLatKey] = place.lat
            prefs[pinnedLonKey] = place.lon
            val recents = (listOf(place) + (prefs[recentsKey]?.let(::parsePlaces) ?: emptyList()))
                .distinctBy { it.label }
                .take(5)
            prefs[recentsKey] = JSONArray(recents.map { p ->
                JSONObject().put("label", p.label).put("lat", p.lat).put("lon", p.lon)
            }).toString()
        }
    }

    /** Back to following the device location. */
    suspend fun unpinPlace() {
        context.dataStore.edit { prefs ->
            prefs.remove(pinnedLabelKey)
            prefs.remove(pinnedLatKey)
            prefs.remove(pinnedLonKey)
        }
    }

    suspend fun setFahrenheit(fahrenheit: Boolean) {
        context.dataStore.edit { it[fahrenheitKey] = fahrenheit }
    }

    suspend fun cachedForecast(): CachedForecast? {
        val prefs = context.dataStore.data.first()
        val json = prefs[cacheJsonKey] ?: return null
        val forecast = runCatching { OpenMeteo.parseForecast(JSONObject(json)) }.getOrNull()
            ?: return null
        return CachedForecast(forecast, prefs[cacheLabelKey] ?: "", prefs[cacheAtKey] ?: 0L)
    }

    suspend fun saveCache(rawJson: String, placeLabel: String, fetchedAt: Long) {
        context.dataStore.edit { prefs ->
            prefs[cacheJsonKey] = rawJson
            prefs[cacheLabelKey] = placeLabel
            prefs[cacheAtKey] = fetchedAt
        }
    }

    private fun parsePlaces(json: String): List<Place> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val place = array.getJSONObject(i)
            Place(place.getString("label"), place.getDouble("lat"), place.getDouble("lon"))
        }.filter { it.isValid }
    }.getOrDefault(emptyList())
}
