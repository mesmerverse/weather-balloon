package com.mesmerverse.weatherballoon.data

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mesmerverse.weatherballoon.net.MAX_TILE_BYTES
import com.mesmerverse.weatherballoon.net.httpGetBytes
import com.mesmerverse.weatherballoon.net.httpGetJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** One radar frame, already cropped to the loop's viewport. */
class RadarFrame(
    val image: ImageBitmap,
    val minutesAgo: Int,
    /** When the radar mosaic was valid, if IEM's metadata said; else null. */
    val validAt: Instant?,
)

/**
 * A short radar loop around a point: the built-in base map plus NEXRAD
 * frames, all cropped to the same viewport centred on the point (the map at
 * 2x, the radar at 1x), so the UI just stacks images and moves nothing.
 *
 * [map] packs three masks: R = ink (lines, labels), G = label halos,
 * B = water. RadarCard colors each one from the sky palette.
 */
class RadarLoop(val map: ImageBitmap, val frames: List<RadarFrame>)

/**
 * NEXRAD base reflectivity from the Iowa Environmental Mesonet's tile cache
 * (keyless, public NOAA data), over a base map built into the app
 * (tools/basemap: Census + Natural Earth, public domain). CONUS only: the
 * n0q mosaic doesn't cover Alaska, Hawaii or the territories.
 *
 * Radar tile requests carry no coordinates, just which ~240 km map squares
 * are in view, so they reveal less than the already-rounded forecast
 * request does. The map itself makes no requests at all.
 */
object IemRadar {

    // Zoom 7 is ~1.2 km/px at 40°N, about the mosaic's native resolution, and
    // the 384 px viewport then spans ~360 km: a regional "what's coming" view.
    private const val ZOOM = 7
    private const val TILE = 256
    const val VIEW_W = 384
    const val VIEW_H = 288

    /** Oldest first, so the loop plays forward and ends on "now". */
    private val MINUTES_AGO = listOf(50, 40, 30, 20, 10, 0)

    // Six frames x up to nine tiles is a burst; keep it polite.
    private val requests = Semaphore(4)

    // Built-in map tiles are 2x (512 px per zoom-7 tile) so labels stay
    // sharp at the card's on-screen size; the radar data is coarser anyway.
    private const val MAP_SCALE = 2

    fun covers(lat: Double, lon: Double): Boolean = lat in 24.0..50.0 && lon in -126.0..-66.0

    suspend fun fetchLoop(lat: Double, lon: Double, assets: AssetManager): RadarLoop = coroutineScope {
        require(covers(lat, lon)) { "Outside NEXRAD coverage" }
        val tiles = tilesInView(lat, lon)

        val validAt = async { runCatching { mosaicValidTime() }.getOrNull() }
        val map = async(Dispatchers.IO) {
            composite(tiles, MAP_SCALE) { x, y -> mapTile(x, y, assets) }
        }
        val frames = MINUTES_AGO.map { minutes ->
            async {
                val layer = if (minutes == 0) "nexrad-n0q-900913" else "nexrad-n0q-900913-m%02dm".format(minutes)
                composite(tiles, 1) { x, y ->
                    fetchTile("https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/$layer/$ZOOM/$x/$y.png")
                }
            }
        }.awaitAll()

        val valid = validAt.await()
        RadarLoop(
            map = map.await().asImageBitmap(),
            frames = frames.mapIndexed { i, bitmap ->
                val minutes = MINUTES_AGO[i]
                RadarFrame(bitmap.asImageBitmap(), minutes, valid?.minusSeconds(minutes * 60L))
            },
        )
    }

    /** The viewport in world pixels at [ZOOM], plus the (<= 3x3) tiles it touches. */
    private class View(val left: Int, val top: Int, val xs: IntRange, val ys: IntRange)

    private fun tilesInView(lat: Double, lon: Double): View {
        // Web Mercator, as every XYZ tile server uses.
        val worldPx = TILE * (1 shl ZOOM).toDouble()
        val latRad = Math.toRadians(lat)
        val cx = (lon + 180) / 360 * worldPx
        val cy = (1 - ln(tan(latRad) + 1 / kotlin.math.cos(latRad)) / PI) / 2 * worldPx
        val left = floor(cx - VIEW_W / 2.0).toInt()
        val top = floor(cy - VIEW_H / 2.0).toInt()
        return View(
            left = left,
            top = top,
            xs = Math.floorDiv(left, TILE)..Math.floorDiv(left + VIEW_W - 1, TILE),
            ys = Math.floorDiv(top, TILE)..Math.floorDiv(top + VIEW_H - 1, TILE),
        )
    }

    private suspend fun composite(
        view: View,
        scale: Int,
        tile: suspend (Int, Int) -> Bitmap?,
    ): Bitmap = coroutineScope {
        val parts = view.ys.flatMap { y -> view.xs.map { x -> Triple(x, y, async { tile(x, y) }) } }
        val out = Bitmap.createBitmap(VIEW_W * scale, VIEW_H * scale, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        for ((x, y, bitmap) in parts) {
            // A missing map tile (edge of the built-in set) just stays blank.
            val b = bitmap.await() ?: continue
            canvas.drawBitmap(
                b,
                ((x * TILE - view.left) * scale).toFloat(),
                ((y * TILE - view.top) * scale).toFloat(),
                null,
            )
        }
        out
    }

    private fun mapTile(x: Int, y: Int, assets: AssetManager): Bitmap? =
        runCatching { assets.open("basemap/${x}_$y.webp").use { BitmapFactory.decodeStream(it) } }.getOrNull()

    private suspend fun fetchTile(url: String): Bitmap =
        decodeTile(requests.withPermit { httpGetBytes(url, MAX_TILE_BYTES) })

    private fun decodeTile(bytes: ByteArray): Bitmap {
        // Check dimensions before allocating: a hostile "PNG" could claim to
        // be enormous and OOM the decode.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth == TILE && bounds.outHeight == TILE) { "Not a map tile" }
        return requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "Undecodable tile" }
    }

    /** When the current ("0 minutes ago") mosaic was valid. */
    private suspend fun mosaicValidTime(): Instant {
        val json = httpGetJson("https://mesonet.agron.iastate.edu/data/gis/images/4326/USCOMP/n0q_0.json")
        val valid = Instant.parse(json.getJSONObject("meta").getString("valid"))
        // A clock-skewed or bogus stamp would label the loop nonsensically.
        require(valid.isAfter(Instant.now().minusSeconds(3 * 3600)) &&
            valid.isBefore(Instant.now().plusSeconds(3600))) { "Implausible radar time" }
        return valid
    }
}
