package com.mesmerverse.weatherballoon.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mesmerverse.weatherballoon.WeatherViewModel
import com.mesmerverse.weatherballoon.data.CurrentConditions
import com.mesmerverse.weatherballoon.data.DailyItem
import com.mesmerverse.weatherballoon.data.Forecast
import com.mesmerverse.weatherballoon.data.HourlyItem
import com.mesmerverse.weatherballoon.ui.theme.SkyPalette
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    state: WeatherViewModel.UiState,
    palette: SkyPalette,
    onRefresh: () -> Unit,
    onToggleUnit: () -> Unit,
    onPickPlace: (com.mesmerverse.weatherballoon.data.Place) -> Unit,
    onFollowDevice: () -> Unit,
    onSearch: (String) -> Unit,
    onToggleRadar: () -> Unit,
    onRetryRadar: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val is24h = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    var sheetOpen by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(palette.top, palette.bottom)))
    ) {
        val refreshing = state.loading && state.forecast != null
        val refreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            state = refreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = refreshState,
                    isRefreshing = refreshing,
                    containerColor = palette.bottom,
                    color = palette.halo,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                TopBar(
                    state = state,
                    palette = palette,
                    onOpenPlaces = { sheetOpen = true },
                    onToggleUnit = onToggleUnit,
                )

                val forecast = state.forecast
                when {
                    forecast != null -> ForecastBody(
                        forecast, state, palette, is24h,
                        onToggleRadar = onToggleRadar,
                        onRetryRadar = onRetryRadar,
                    )
                    state.loading -> Box(
                        Modifier.fillMaxWidth().height(480.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = palette.halo)
                    }
                    state.needsPermission -> FirstRun(
                        palette = palette,
                        onRequestPermission = onRequestPermission,
                        onSearchInstead = { sheetOpen = true },
                    )
                    state.locationUnavailable -> LocationUnavailable(
                        palette = palette,
                        onSearch = { sheetOpen = true },
                        onRetry = onRefresh,
                    )
                    else -> EmptyError(
                        error = state.error,
                        palette = palette,
                        onRefresh = onRefresh,
                        onSearch = { sheetOpen = true },
                    )
                }
            }
        }

        if (sheetOpen) {
            PlaceSheet(
                state = state,
                palette = palette,
                onDismiss = { sheetOpen = false },
                onSearch = onSearch,
                onPick = onPickPlace,
                onFollowDevice = onFollowDevice,
            )
        }
    }
}

@Composable
private fun TopBar(
    state: WeatherViewModel.UiState,
    palette: SkyPalette,
    onOpenPlaces: () -> Unit,
    onToggleUnit: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clickable(onClick = onOpenPlaces, onClickLabel = "Change location")
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.followingDevice) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = palette.dim,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                state.placeLabel.ifEmpty { "Choose a place" },
                style = MaterialTheme.typography.titleMedium,
                color = palette.ink,
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = palette.dim,
            )
        }
        Spacer(Modifier.weight(1f))
        // The current unit with a chevron, mirroring the location header: a
        // bare "°F" button reads as "switch to °F" to half of everyone.
        Box {
            var unitMenuOpen by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .clickable(
                        onClick = { unitMenuOpen = true },
                        onClickLabel = "Change temperature unit",
                    )
                    .heightIn(min = 48.dp)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (state.fahrenheit) "°F" else "°C",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.ink,
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = palette.dim,
                )
            }
            DropdownMenu(expanded = unitMenuOpen, onDismissRequest = { unitMenuOpen = false }) {
                // The unit also drives wind and precipitation; say so.
                listOf(true to "Fahrenheit · mph", false to "Celsius · km/h").forEach { (f, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        trailingIcon = if (state.fahrenheit == f) {
                            { Icon(Icons.Default.Check, contentDescription = "Selected") }
                        } else null,
                        onClick = {
                            unitMenuOpen = false
                            if (state.fahrenheit != f) onToggleUnit()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ForecastBody(
    forecast: Forecast,
    state: WeatherViewModel.UiState,
    palette: SkyPalette,
    is24h: Boolean,
    onToggleRadar: () -> Unit,
    onRetryRadar: () -> Unit,
) {
    Hero(
        forecast, state.fahrenheit, palette, is24h,
        radarAvailable = state.radarAvailable,
        radarOpen = state.radarOpen,
        onToggleRadar = onToggleRadar,
    )

    // Radar opens in place under the halo, pushing the forecast down.
    AnimatedVisibility(
        visible = state.radarOpen,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        RadarPanel(state, palette, is24h, onRetry = onRetryRadar)
    }

    // A real error must not dress like the footer; the routine stale note can.
    if (state.error != null) {
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            Text(
                state.error,
                style = MaterialTheme.typography.bodySmall,
                color = palette.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .background(palette.halo.copy(alpha = 0.18f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    } else if (state.stale) {
        Text(
            "Showing the last saved forecast",
            style = MaterialTheme.typography.bodySmall,
            color = palette.dim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        )
    }

    // "Now" is device time at the forecast location's UTC offset, so a stale
    // cache or a place in another timezone still lines up with the clock.
    val nowIso = LocalDateTime.now(ZoneOffset.ofTotalSeconds(forecast.utcOffsetSeconds)).toString()

    // Hourly data covers the whole week; the strip shows now + 24h.
    val nowIndex = forecast.hourly.indexOfLast { it.time <= nowIso }
        .coerceAtLeast(0)
    SectionLabel("NEXT 24 HOURS", palette)
    HourlyStrip(
        forecast.hourly.drop(nowIndex).take(24),
        state.fahrenheit, palette, is24h,
        nowFirst = true,
    )

    SectionLabel("NEXT 7 DAYS", palette)
    DailyList(forecast, state.fahrenheit, palette, is24h, nowIso)

    // CC-BY data: the attribution links to the source.
    val uriHandler = LocalUriHandler.current
    Text(
        buildString {
            append("Weather by Open-Meteo")
            state.fetchedAt?.let { append("  ·  updated ${updatedLabel(it, is24h)}") }
        },
        style = MaterialTheme.typography.bodySmall,
        color = palette.dim,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open open-meteo.com") {
                uriHandler.openUri("https://open-meteo.com")
            }
            .padding(top = 28.dp, bottom = 24.dp),
    )
}

/**
 * The signature: the current temperature wears the sun's 22° halo, with the
 * two bright companion spots flanking it. The halo fades as clouds move in.
 */
@Composable
private fun Hero(
    forecast: Forecast,
    fahrenheit: Boolean,
    palette: SkyPalette,
    is24h: Boolean,
    radarAvailable: Boolean,
    radarOpen: Boolean,
    onToggleRadar: () -> Unit,
) {
    val current = forecast.current
    val ringAlpha = when (sky(current.code)) {
        Sky.CLEAR -> 1f
        Sky.PARTLY -> 0.65f
        Sky.OVERCAST, Sky.FOG -> 0.35f
        Sky.RAIN, Sky.SNOW, Sky.STORM -> 0.25f
    }

    // Cap the hero on short screens so the hourly strip stays above the fold.
    val heroHeight = minOf(380.dp, (LocalConfiguration.current.screenHeightDp * 0.45f).dp)
    Box(
        Modifier.fillMaxWidth().height(heroHeight),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            // Keep the parhelia fully on screen.
            val radius = (size.minDimension * 0.44f)
                .coerceAtMost(size.width / 2f - 26.dp.toPx())
            val halo = palette.halo

            // Soft glow the number sits in.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(halo.copy(alpha = 0.28f * ringAlpha), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
            // The 22° halo.
            drawCircle(
                color = halo.copy(alpha = 0.6f * ringAlpha),
                radius = radius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx()),
            )
            // Parhelia, left and right of the sun.
            listOf(-1f, 1f).forEach { side ->
                val dog = Offset(center.x + side * radius, center.y)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            halo.copy(alpha = 0.9f * ringAlpha),
                            halo.copy(alpha = 0.25f * ringAlpha),
                            Color.Transparent,
                        ),
                        center = dog,
                        radius = 22.dp.toPx(),
                    ),
                    radius = 22.dp.toPx(),
                    center = dog,
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${displayTemp(current.tempC, fahrenheit)}°",
                style = MaterialTheme.typography.displayLarge,
                color = palette.ink,
            )
            Text(
                wmoLabel(current.code),
                style = MaterialTheme.typography.headlineSmall,
                color = palette.ink,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Feels ${displayTemp(current.apparentC, fahrenheit)}°  ·  " +
                    "${current.humidity}%  ·  ${displayWind(current.windKmh, fahrenheit)}",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.dim,
            )
            Spacer(Modifier.height(4.dp))
            val today = forecast.daily.first()
            Text(
                "↑ ${clockLabel(today.sunrise, is24h)}   ↓ ${clockLabel(today.sunset, is24h)}",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.dim,
            )
            if (radarAvailable) {
                Spacer(Modifier.height(2.dp))
                RadarButton(radarOpen, palette, onToggleRadar)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, palette: SkyPalette) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = palette.dim,
        modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 12.dp),
    )
}

@Composable
private fun HourlyStrip(
    hours: List<HourlyItem>,
    fahrenheit: Boolean,
    palette: SkyPalette,
    is24h: Boolean,
    nowFirst: Boolean = false,
) {
    // The LazyRow keeps its scroll offset across recompositions, so without
    // this the strip can come back showing afternoon hours with "Now" (its
    // whole orientation device) off-screen. Snap home whenever the window
    // advances.
    val listState = rememberLazyListState()
    LaunchedEffect(hours.firstOrNull()?.time) { listState.scrollToItem(0) }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(hours.size) { i ->
            val hour = hours[i]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                // One TalkBack swipe stop per hour, not four.
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Text(
                    if (nowFirst && i == 0) "Now" else hourLabel(hour.time, is24h),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.dim,
                )
                Text(
                    wmoEmoji(hour.code, hour.isDay),
                    fontSize = 22.sp,
                    // Read our label, not the raw emoji name.
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = wmoLabel(hour.code)
                    },
                )
                Text(
                    "${displayTemp(hour.tempC, fahrenheit)}°",
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.ink,
                )
                Text(
                    if (hour.precipProbability >= 10) "${hour.precipProbability}%" else " ",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.rain,
                )
            }
        }
    }
}

@Composable
private fun DailyList(
    forecast: Forecast,
    fahrenheit: Boolean,
    palette: SkyPalette,
    is24h: Boolean,
    nowIso: String,
) {
    val today = nowIso.take(10)
    val days = forecast.daily
    val weekMin = days.minOf { it.minC }
    val weekMax = days.maxOf { it.maxC }
    var expandedDate by remember { mutableStateOf<String?>(null) }

    Column {
        days.forEachIndexed { index, day ->
            if (index > 0) {
                HorizontalDivider(
                    color = palette.track,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            val expanded = expandedDate == day.date
            DayRow(
                day = day,
                label = dayLabel(day.date, today),
                isToday = day.date == today,
                expanded = expanded,
                weekMin = weekMin,
                weekMax = weekMax,
                fahrenheit = fahrenheit,
                palette = palette,
                onToggle = { expandedDate = if (expanded) null else day.date },
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                DayDetail(day, forecast, fahrenheit, palette, is24h, nowIso)
            }
        }
    }
}

@Composable
private fun DayRow(
    day: DailyItem,
    label: String,
    isToday: Boolean,
    expanded: Boolean,
    weekMin: Double,
    weekMax: Double,
    fahrenheit: Boolean,
    palette: SkyPalette,
    onToggle: () -> Unit,
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .clickable(
            onClickLabel = if (expanded) "Hide hourly forecast" else "Show hourly forecast",
            onClick = onToggle,
        )
        .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
        .padding(horizontal = 24.dp)
        .heightIn(min = 48.dp)

    val dayName = @Composable { modifier: Modifier ->
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = palette.ink,
            maxLines = 1,
            modifier = modifier,
        )
    }
    val emoji = @Composable {
        Text(
            wmoEmoji(day.code, isDay = true),
            fontSize = 17.sp,
            // Read our label, not the raw emoji name.
            modifier = Modifier.clearAndSetSemantics { contentDescription = wmoLabel(day.code) },
        )
    }
    val precipText = @Composable { modifier: Modifier ->
        Text(
            if (day.precipProbability >= 10) "${day.precipProbability}%" else "",
            style = MaterialTheme.typography.bodySmall,
            color = palette.rain,
            maxLines = 1,
            modifier = modifier,
        )
    }
    val minText = @Composable { modifier: Modifier ->
        Text(
            "${displayTemp(day.minC, fahrenheit)}°",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.dim,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = modifier,
        )
    }
    val maxText = @Composable { modifier: Modifier ->
        Text(
            "${displayTemp(day.maxC, fahrenheit)}°",
            style = MaterialTheme.typography.bodyLarge,
            color = palette.ink,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = modifier,
        )
    }
    val rangeBar = @Composable { modifier: Modifier ->
        RangeBar(
            minC = day.minC,
            maxC = day.maxC,
            weekMin = weekMin,
            weekMax = weekMax,
            color = if (isToday) palette.halo else palette.ink.copy(alpha = 0.55f),
            track = palette.track,
            modifier = modifier,
        )
    }
    val chevron = @Composable {
        val angle by animateFloatAsState(if (expanded) 180f else 0f)
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = palette.dim,
            modifier = Modifier.padding(start = 4.dp).size(18.dp).rotate(angle),
        )
    }

    // The single-line row holds sp-sized text in dp-sized columns; scaling the
    // columns with the font keeps glyphs from clipping, but past ~1.35 the
    // fixed cells crush the range bar into a dot. Above that, split each day
    // onto two lines and let everything size itself.
    val fontScale = LocalDensity.current.fontScale
    if (fontScale <= 1.35f) {
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            dayName(Modifier.width(56.dp * fontScale))
            Box(Modifier.width(30.dp * fontScale)) { emoji() }
            precipText(Modifier.width(38.dp * fontScale))
            minText(Modifier.width(38.dp * fontScale))
            rangeBar(Modifier.weight(1f).padding(horizontal = 8.dp))
            maxText(Modifier.width(46.dp * fontScale))
            chevron()
        }
    } else {
        Column(rowModifier.padding(vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                dayName(Modifier.weight(1f))
                emoji()
                Spacer(Modifier.width(10.dp))
                precipText(Modifier)
                chevron()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                minText(Modifier)
                rangeBar(Modifier.weight(1f).padding(horizontal = 10.dp))
                maxText(Modifier)
            }
        }
    }
}

/** Expanded view of one day: its hour-by-hour strip plus the day's vitals. */
@Composable
private fun DayDetail(
    day: DailyItem,
    forecast: Forecast,
    fahrenheit: Boolean,
    palette: SkyPalette,
    is24h: Boolean,
    nowIso: String,
) {
    val isToday = day.date == nowIso.take(10)
    val hours = forecast.hourly.filter { hour ->
        hour.time.startsWith(day.date) && (!isToday || hour.time >= nowIso.take(13))
    }

    // A whisper of ink behind the block so it reads as "inside" the row,
    // not as a sibling section. Ink-based, so it works day and night.
    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.ink.copy(alpha = 0.05f))
            .padding(bottom = 16.dp),
    ) {
        Text(
            buildString {
                append("↑ ${clockLabel(day.sunrise, is24h)}   ↓ ${clockLabel(day.sunset, is24h)}")
                append("   ·   wind ${displayWind(day.windMaxKmh, fahrenheit)}")
                append("   ·   UV ${day.uvMax.roundToInt()}")
                if (day.precipSumMm > 0) {
                    append("   ·   ${displayPrecip(day.precipSumMm, fahrenheit)}")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = palette.dim,
            modifier = Modifier.padding(start = 24.dp, top = 2.dp, bottom = 12.dp),
        )
        if (hours.isNotEmpty()) {
            HourlyStrip(hours, fahrenheit, palette, is24h, nowFirst = isToday)
        }
    }
}

/** Each day's min–max span drawn on the week's shared temperature scale. */
@Composable
private fun RangeBar(
    minC: Double,
    maxC: Double,
    weekMin: Double,
    weekMax: Double,
    color: Color,
    track: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.height(5.dp)) {
        val span = (weekMax - weekMin).coerceAtLeast(0.1)
        val startX = ((minC - weekMin) / span).toFloat() * size.width
        val endX = ((maxC - weekMin) / span).toFloat() * size.width
        val r = size.height / 2f
        drawRoundRect(
            color = track,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        )
        // Enforce a minimum bar length without drawing past the track.
        val barWidth = (endX - startX).coerceAtLeast(size.height)
        drawRoundRect(
            color = color,
            topLeft = Offset(startX.coerceAtMost(size.width - barWidth), 0f),
            size = androidx.compose.ui.geometry.Size(barWidth, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        )
    }
}

@Composable
private fun FirstRun(
    palette: SkyPalette,
    onRequestPermission: () -> Unit,
    onSearchInstead: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("☀", fontSize = 44.sp, color = palette.halo)
        Spacer(Modifier.height(20.dp))
        Text(
            "Where are you?",
            style = MaterialTheme.typography.headlineSmall,
            color = palette.ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Weather Balloon can follow your location — city-level only, rounded before " +
                "it's sent to Open-Meteo — or stay pinned to a place you pick.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.dim,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRequestPermission,
            shape = CircleShape,
            colors = weatherBalloonButtonColors(palette),
        ) {
            Text("Use my location")
        }
        TextButton(onClick = onSearchInstead) {
            Text("Search for a place instead", color = palette.dim)
        }
    }
}

// White on the day halo gold is only 3.6:1; buttons get a deeper gold by day.
// Night keeps the brighter halo with dark text — plenty of contrast there.
@Composable
private fun weatherBalloonButtonColors(palette: SkyPalette) = ButtonDefaults.buttonColors(
    containerColor = if (palette.isDark) palette.halo else Color(0xFF9A6414),
    contentColor = if (palette.isDark) palette.top else Color.White,
)

@Composable
private fun LocationUnavailable(
    palette: SkyPalette,
    onSearch: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Halo-tinted vector, matching FirstRun's tinted glyph — the emoji
        // rendered OEM-dependent and outside the palette.
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            tint = palette.halo,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Couldn't find your location",
            style = MaterialTheme.typography.headlineSmall,
            color = palette.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Search for your city below to get a forecast right away.\n\n" +
                "To use your current location automatically: on GrapheneOS turn " +
                "on Settings › Location › Network location, or step outside for a " +
                "GPS fix.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.dim,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onSearch,
            shape = CircleShape,
            colors = weatherBalloonButtonColors(palette),
        ) {
            Text("Search for a place")
        }
        TextButton(onClick = onRetry) {
            Text("Try current location again", color = palette.dim)
        }
    }
}

@Composable
private fun EmptyError(
    error: String?,
    palette: SkyPalette,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            error ?: "Nothing to show yet",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.dim,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onRefresh) {
            Text("Try again", color = palette.halo)
        }
        TextButton(onClick = onSearch) {
            Text("Search for a place", color = palette.dim)
        }
    }
}
