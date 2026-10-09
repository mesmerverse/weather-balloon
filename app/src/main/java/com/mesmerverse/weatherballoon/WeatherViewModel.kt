package com.mesmerverse.weatherballoon

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mesmerverse.weatherballoon.data.Forecast
import com.mesmerverse.weatherballoon.data.IemRadar
import com.mesmerverse.weatherballoon.data.LocationRepository
import com.mesmerverse.weatherballoon.data.OpenMeteo
import com.mesmerverse.weatherballoon.data.Place
import com.mesmerverse.weatherballoon.data.RadarLoop
import com.mesmerverse.weatherballoon.data.WeatherStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

class WeatherViewModel(app: Application) : AndroidViewModel(app) {

    data class UiState(
        val loading: Boolean = true,
        val forecast: Forecast? = null,
        val placeLabel: String = "",
        /** True when the forecast follows the device location (nothing pinned). */
        val followingDevice: Boolean = true,
        /** True when the shown forecast came from the cache, not this session. */
        val stale: Boolean = false,
        val fetchedAt: Long? = null,
        val error: String? = null,
        /** Following the device but the location permission isn't granted. */
        val needsPermission: Boolean = false,
        /**
         * Permission is granted but no position could be obtained (no cached
         * fix and nothing produced a fresh one). Distinct from [error] so the
         * UI can offer the place search + platform-specific guidance instead
         * of a bare retry.
         */
        val locationUnavailable: Boolean = false,
        val fahrenheit: Boolean = false,
        val recentPlaces: List<Place> = emptyList(),
        val searchResults: List<Place> = emptyList(),
        val searching: Boolean = false,
        /** The shown place is inside radar coverage, so the hero offers the button. */
        val radarAvailable: Boolean = false,
        /** The radar card is expanded (it's only fetched while open). */
        val radarOpen: Boolean = false,
        val radarLoading: Boolean = false,
        val radarFailed: Boolean = false,
        /** Radar loop for the shown place; null until opened and loaded. */
        val radar: RadarLoop? = null,
    )

    private val store = WeatherStore(app)
    private val locations = LocationRepository(app)

    private val _state = MutableStateFlow(UiState(fahrenheit = localeFahrenheit()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var refreshJob: Job? = null
    private var searchJob: Job? = null
    private var radarJob: Job? = null
    private var radarPlace: Place? = null
    private var radarFetchedAt = 0L
    /** The place the forecast was last requested for; the radar follows it. */
    private var currentPlace: Place? = null

    init {
        viewModelScope.launch {
            // Last good forecast paints instantly; the network catches up after.
            store.cachedForecast()?.let { cached ->
                _state.update { s ->
                    if (s.forecast == null) s.copy(
                        forecast = cached.forecast,
                        placeLabel = cached.placeLabel,
                        stale = true,
                        fetchedAt = cached.fetchedAt,
                    ) else s
                }
            }
            launch {
                store.settings.collect { settings ->
                    _state.update {
                        it.copy(
                            fahrenheit = settings.fahrenheit ?: localeFahrenheit(),
                            followingDevice = settings.pinnedPlace == null,
                            recentPlaces = settings.recentPlaces,
                        )
                    }
                }
            }
            // Refetch whenever the pinned place changes; also fires the first fetch.
            launch {
                store.settings.map { it.pinnedPlace }.distinctUntilChanged().collect { refresh() }
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, locationUnavailable = false) }
            val place = resolvePlace() ?: run {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            _state.update { it.copy(needsPermission = false, locationUnavailable = false) }
            onPlaceResolved(place)
            runCatching { OpenMeteo.fetchForecast(place.lat, place.lon) }
                .onSuccess { fetch ->
                    val now = System.currentTimeMillis()
                    // A failed save (disk full) only costs the offline copy.
                    runCatching { store.saveCache(fetch.rawJson, place.label, now) }
                    _state.update {
                        it.copy(
                            loading = false,
                            forecast = fetch.forecast,
                            placeLabel = place.label,
                            stale = false,
                            fetchedAt = now,
                            error = null,
                        )
                    }
                }
                .onFailure {
                    _state.update { s ->
                        s.copy(
                            loading = false,
                            error = when {
                                s.forecast == null ->
                                    "Couldn't reach Open-Meteo. Check your connection, then pull down to retry."
                                // The place changed but the fetch for it failed;
                                // the forecast on screen is still the old place's.
                                s.placeLabel != place.label ->
                                    "Couldn't load ${place.label} — still showing ${s.placeLabel}"
                                else -> "Offline — showing the last saved forecast"
                            },
                        )
                    }
                }
        }
    }

    private fun onPlaceResolved(place: Place) {
        currentPlace = place
        val samePlace = place.lat == radarPlace?.lat && place.lon == radarPlace?.lon
        if (!samePlace) {
            radarJob?.cancel()
            radarPlace = null
            _state.update { it.copy(radar = null, radarLoading = false, radarFailed = false) }
        }
        val covered = IemRadar.covers(place.lat, place.lon)
        _state.update { it.copy(radarAvailable = covered, radarOpen = it.radarOpen && covered) }
        // A pull-to-refresh with the card open should refresh what it shows.
        if (covered && _state.value.radarOpen) refreshRadar(place)
    }

    fun toggleRadar() {
        val opening = !_state.value.radarOpen
        _state.update { it.copy(radarOpen = opening) }
        val place = currentPlace ?: return
        // Reopening within a few minutes shows the loop already in hand.
        val fresh = _state.value.radar != null &&
            System.currentTimeMillis() - radarFetchedAt < RADAR_FRESH_MS
        if (opening && !fresh) refreshRadar(place)
    }

    fun retryRadar() {
        currentPlace?.let(::refreshRadar)
    }

    /**
     * Radar runs beside the forecast, never ahead of it or in its error path,
     * and only while the card is open: a hidden card costs no tile requests.
     */
    private fun refreshRadar(place: Place) {
        if (!IemRadar.covers(place.lat, place.lon)) return
        radarJob?.cancel()
        _state.update { it.copy(radarLoading = true, radarFailed = false) }
        radarJob = viewModelScope.launch {
            runCatching { IemRadar.fetchLoop(place.lat, place.lon, getApplication<Application>().assets) }
                .onSuccess { loop ->
                    radarPlace = place
                    radarFetchedAt = System.currentTimeMillis()
                    _state.update { it.copy(radar = loop, radarLoading = false) }
                }
                .onFailure { e ->
                    // Cancellation means a newer fetch took over; leave its state alone.
                    if (e !is kotlinx.coroutines.CancellationException) {
                        _state.update { it.copy(radarLoading = false, radarFailed = true) }
                    }
                }
        }
    }

    /** Refresh on app resume, but only if the data is older than 15 minutes. */
    fun refreshIfStale() {
        // Cold start: the init-triggered refresh is already running.
        if (refreshJob?.isActive == true) return
        val fetchedAt = _state.value.fetchedAt ?: return refresh()
        if (System.currentTimeMillis() - fetchedAt > 15 * 60_000L) refresh()
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) refresh() else _state.update { it.copy(needsPermission = true) }
    }

    fun pinPlace(place: Place) {
        viewModelScope.launch { runCatching { store.pinPlace(place) } }
    }

    fun followDevice() {
        viewModelScope.launch {
            val wasPinned = store.settings.first().pinnedPlace != null
            store.unpinPlace()
            // The settings collector refetches on a pin change; cover the no-op case.
            if (!wasPinned) refresh()
        }
    }

    fun toggleUnit() {
        viewModelScope.launch { store.setFahrenheit(!_state.value.fahrenheit) }
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // debounce keystrokes
            _state.update { it.copy(searching = true) }
            val results = runCatching { OpenMeteo.geocode(query) }.getOrDefault(emptyList())
            _state.update { it.copy(searchResults = results, searching = false) }
        }
    }

    private suspend fun resolvePlace(): Place? {
        store.settings.first().pinnedPlace?.let { return it }
        if (!locations.hasPermission()) {
            _state.update { it.copy(needsPermission = true) }
            return null
        }
        val (rawLat, rawLon) = locations.currentLocation() ?: run {
            // Permission is granted but nothing produced a fix. Surface a
            // dedicated state (not a generic error) so the UI can point at the
            // place search and the platform-specific remedies.
            _state.update { it.copy(locationUnavailable = true) }
            return null
        }
        // Round to ~1 km here, before the fix reaches the geocoder (on most
        // devices a network service) — not just before the Open-Meteo URL —
        // so "coordinates are rounded before any request leaves the device"
        // holds for every request.
        val lat = roundCoordinate(rawLat)
        val lon = roundCoordinate(rawLon)
        return Place(locations.placeName(lat, lon) ?: "My location", lat, lon)
    }

    private companion object {
        // IEM's mosaic updates every ~5 minutes.
        const val RADAR_FRESH_MS = 5 * 60_000L
    }

    private fun roundCoordinate(value: Double): Double = Math.round(value * 100) / 100.0

    private fun localeFahrenheit(): Boolean =
        Locale.getDefault().country in setOf("US", "BS", "BZ", "KY", "PW", "LR", "MM")
}
