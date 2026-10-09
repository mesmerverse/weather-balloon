package com.mesmerverse.weatherballoon.ui

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mesmerverse.weatherballoon.WeatherViewModel
import com.mesmerverse.weatherballoon.data.IemRadar
import com.mesmerverse.weatherballoon.data.RadarLoop
import com.mesmerverse.weatherballoon.ui.theme.SkyPalette
import kotlinx.coroutines.delay

private const val FRAME_MS = 500L
private const val HOLD_LATEST_MS = 1_800L
private const val SWEEP_MS = 4_000

/**
 * The hero's way into the radar: a miniature of the halo itself, with two
 * hairline range rings, a beam, and a sweep that fades out behind it. No
 * fill, no label. Dim and still while closed; halo gold and slowly sweeping
 * while the card is open, so it doubles as the "live" cue and the close.
 */
@Composable
fun RadarButton(open: Boolean, palette: SkyPalette, onClick: () -> Unit) {
    val animate = animationsEnabled()
    val tint by animateColorAsState(if (open) palette.halo else palette.dim, label = "radarTint")
    val spin = rememberInfiniteTransition(label = "radarSweep")
        .animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(SWEEP_MS, easing = LinearEasing)),
            label = "radarAngle",
        )
    Box(
        Modifier
            .size(48.dp)   // touch target; the glyph itself is small
            .clip(CircleShape)
            .clickable(onClickLabel = if (open) "Hide radar" else "Show radar", onClick = onClick)
            .semantics {
                contentDescription = "Radar"
                stateDescription = if (open) "Shown" else "Hidden"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(30.dp)) {
            val hair = 1.dp.toPx()
            val r = size.minDimension / 2f - hair
            // Beam at ~1:30 when still; reading spin only here keeps the
            // closed glyph from recomposing every frame.
            val angle = if (open && animate) spin.value - 90f else -45f

            drawCircle(tint.copy(alpha = tint.alpha * 0.55f), r, center, style = Stroke(hair))
            drawCircle(tint.copy(alpha = tint.alpha * 0.35f), r * 0.5f, center, style = Stroke(hair))
            rotate(angle, center) {
                // The sweep trails the beam (at 0°) by 80°, fading to nothing.
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.78f to Color.Transparent,
                        1f to tint.copy(alpha = tint.alpha * 0.45f),
                        center = center,
                    ),
                    startAngle = -80f,
                    sweepAngle = 80f,
                    useCenter = true,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2, r * 2),
                )
                drawLine(tint, center, Offset(center.x + r, center.y), strokeWidth = hair * 1.2f, cap = StrokeCap.Round)
            }
            drawCircle(tint, hair * 1.4f, center)
        }
    }
}

@Composable
private fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

/** What sits under the hero while the radar is open: the loop, or its loading/failed stand-in. */
@Composable
fun RadarPanel(
    state: WeatherViewModel.UiState,
    palette: SkyPalette,
    is24h: Boolean,
    onRetry: () -> Unit,
) {
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        val radar = state.radar
        if (radar != null) {
            val code = state.forecast?.current?.code
            RadarCard(radar, palette, is24h, hereNow = code?.takeIf(::precipitating)?.let(::wmoLabel))
            return@Column
        }
        Box(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .aspectRatio(IemRadar.VIEW_W.toFloat() / IemRadar.VIEW_H)
                .clip(RoundedCornerShape(16.dp))
                .background(palette.track)
                .then(
                    if (state.radarFailed) Modifier.clickable(onClickLabel = "Retry radar", onClick = onRetry)
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (state.radarFailed) {
                Text(
                    "Couldn't load radar. Tap to retry.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.dim,
                )
            } else {
                CircularProgressIndicator(color = palette.halo)
            }
        }
    }
}

/**
 * The last hour of radar around the forecast place, looping. The built-in
 * map is drawn in the sky palette's own muted tones (see [MapColors]) so the
 * only strong color on the card is precipitation; labels sit above the radar
 * on a knock-out halo, and the place is a halo-gold dot in the middle.
 * Tap to pause; with animations turned off system-wide it just shows "now".
 */
@Composable
private fun RadarCard(radar: RadarLoop, palette: SkyPalette, is24h: Boolean, hereNow: String?) {
    val animate = animationsEnabled()
    val last = radar.frames.lastIndex
    var index by remember(radar) { mutableIntStateOf(last) }
    var playing by remember(radar) { mutableStateOf(animate) }

    LaunchedEffect(radar, playing) {
        if (!playing) return@LaunchedEffect
        while (true) {
            delay(if (index == last) HOLD_LATEST_MS else FRAME_MS)
            index = (index + 1) % radar.frames.size
        }
    }

    val frame = radar.frames[index]
    val timeText = frame.validAt?.let { updatedLabel(it.toEpochMilli(), is24h) }
        ?: if (frame.minutesAgo == 0) "Now" else "−${frame.minutesAgo} min"
    val map = remember(palette) { MapColors(palette) }

    Box(
        Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .aspectRatio(IemRadar.VIEW_W.toFloat() / IemRadar.VIEW_H)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = if (playing) "Pause radar" else "Play radar") {
                playing = !playing
            }
            .semantics {
                contentDescription = "Radar map around this place"
                stateDescription = listOfNotNull(
                    hereNow?.let { "$it here now" },
                    timeText,
                    if (playing) "playing" else "paused",
                ).joinToString(", ")
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val dst = IntSize(size.width.toInt(), size.height.toInt())
            // land -> water -> radar -> label halos -> lines and labels
            drawRect(map.land)
            drawImage(radar.map, dstSize = dst, colorFilter = map.water, filterQuality = FilterQuality.Low)
            drawImage(frame.image, dstSize = dst, alpha = 0.9f, filterQuality = FilterQuality.Low)
            drawImage(radar.map, dstSize = dst, colorFilter = map.halo, filterQuality = FilterQuality.Low)
            drawImage(radar.map, dstSize = dst, colorFilter = map.ink, filterQuality = FilterQuality.Low)
            val r = 5.dp.toPx()
            drawCircle(palette.halo, r, center)
            drawCircle(palette.ink, r, center, style = Stroke(1.5.dp.toPx()))
        }

        // Radar colors look the same for rain and snow (and understate snow),
        // so name what's falling here, from the forecast's current weather.
        if (hereNow != null) {
            Text(
                "$hereNow here now",
                style = MaterialTheme.typography.bodySmall,
                color = palette.ink,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .background(palette.bottom.copy(alpha = 0.85f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .background(palette.bottom.copy(alpha = 0.85f), CircleShape)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(timeText, style = MaterialTheme.typography.bodySmall, color = palette.ink)
            // Loop position: one dot per frame, the current one in halo gold.
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                radar.frames.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(5.dp)
                            .background(if (i == index) palette.halo else palette.dim.copy(alpha = 0.4f), CircleShape)
                    )
                }
            }
        }
    }

    // All public domain, so no attribution is required; credit them anyway.
    Text(
        "Radar: NOAA NEXRAD via Iowa Environmental Mesonet  ·  Map: US Census, Natural Earth",
        style = MaterialTheme.typography.bodySmall,
        color = palette.dim,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

/**
 * The map's masks (R ink, G halo, B water) colored from the sky: land is the
 * middle of the sky gradient (lifted toward paper by day), water a shade
 * deeper, ink the palette's own text color, halos cut back to land.
 */
private class MapColors(palette: SkyPalette) {
    val land = lerp(palette.top, palette.bottom, 0.5f).let {
        if (palette.isDark) it else lerp(it, Color.White, 0.35f)
    }
    private val waterColor =
        if (palette.isDark) lerp(land, Color.Black, 0.3f) else lerp(land, Color(0xFF6F93B3), 0.35f)

    val water = maskFilter(channel = 2, color = waterColor)
    val halo = maskFilter(channel = 1, color = land.copy(alpha = 0.9f))
    val ink = maskFilter(channel = 0, color = palette.ink.copy(alpha = 0.85f))
}

/** Paint [color] with the given channel of the map image as its alpha. */
private fun maskFilter(channel: Int, color: Color): ColorFilter {
    val a = FloatArray(3).also { it[channel] = color.alpha }
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0f, 0f, 0f, 0f, color.red * 255f,
                0f, 0f, 0f, 0f, color.green * 255f,
                0f, 0f, 0f, 0f, color.blue * 255f,
                a[0], a[1], a[2], 0f, 0f,
            )
        )
    )
}
