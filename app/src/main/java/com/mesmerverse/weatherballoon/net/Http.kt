package com.mesmerverse.weatherballoon.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

// Our largest real response (the 7-day forecast) is ~7 KB; this is ~9x
// headroom, and anything bigger means the endpoint is misbehaving.
private const val MAX_BODY_BYTES = 64 * 1024

// Radar tiles are 256x256 PNGs of ~10 KB; this is generous headroom.
internal const val MAX_TILE_BYTES = 256 * 1024

// readTimeout is per-read() and resets on every byte, so a slow-drip server
// could hold an IO thread indefinitely; this bounds the whole request.
private const val TOTAL_DEADLINE_MS = 30_000L

// Identify the app, as polite API clients do. Name only: no URL or contact
// that would tie requests to anyone.
private const val USER_AGENT = "WeatherBalloon/1.2 (Android)"

internal suspend fun httpGetJson(url: String): JSONObject =
    JSONObject(httpGetBytes(url, MAX_BODY_BYTES).decodeToString())

internal suspend fun httpGetBytes(url: String, maxBytes: Int): ByteArray = coroutineScope {
    val connection = URL(url).openConnection()
    check(connection is HttpsURLConnection) { "Refusing non-HTTPS URL" }
    connection.connectTimeout = 10_000
    connection.readTimeout = 10_000
    // Don't let a compromised endpoint bounce the request (and its query
    // string) to an arbitrary host.
    connection.instanceFollowRedirects = false
    connection.setRequestProperty("User-Agent", USER_AGENT)
    // Blocking socket reads ignore coroutine cancellation; disconnecting from
    // the watchdog makes them throw promptly on cancel or deadline. It also
    // fires on normal completion, which is fine — the body is already read.
    val watchdog = launch(Dispatchers.IO) {
        try {
            delay(TOTAL_DEADLINE_MS)
        } finally {
            connection.disconnect()
        }
    }
    try {
        runInterruptible(Dispatchers.IO) {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            val body = connection.inputStream.use { it.readNBytes(maxBytes + 1) }
            check(body.size <= maxBytes) { "Response exceeds $maxBytes bytes" }
            body
        }
    } finally {
        watchdog.cancel()
    }
}
