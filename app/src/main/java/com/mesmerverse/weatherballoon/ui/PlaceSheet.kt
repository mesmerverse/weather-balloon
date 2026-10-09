package com.mesmerverse.weatherballoon.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.mesmerverse.weatherballoon.WeatherViewModel
import com.mesmerverse.weatherballoon.data.Place
import com.mesmerverse.weatherballoon.ui.theme.SkyPalette

/**
 * The location picker. Type a couple of letters and Open-Meteo's geocoder
 * does the rest — no map, no coordinates, no fuss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSheet(
    state: WeatherViewModel.UiState,
    palette: SkyPalette,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onPick: (Place) -> Unit,
    onFollowDevice: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    // Searching is the sheet's whole point — put the cursor there on open.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onSearch(it)
                },
                placeholder = { Text("Search for a city or town") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )

            Spacer(Modifier.height(8.dp))

            if (state.searching) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = palette.halo,
                )
            }

            if (query.isBlank()) {
                SheetRow(
                    icon = { Icon(Icons.Default.LocationOn, null, tint = palette.halo, modifier = Modifier.size(20.dp)) },
                    title = "Use my location",
                    subtitle = "City-level only — coordinates are rounded before anything is sent",
                ) {
                    onFollowDevice()
                    onDismiss()
                }
                if (state.recentPlaces.isNotEmpty()) {
                    Text(
                        "RECENT",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    state.recentPlaces.forEach { place ->
                        SheetRow(icon = null, title = place.label, subtitle = null) {
                            onPick(place)
                            onDismiss()
                        }
                    }
                }
            } else {
                if (state.searchResults.isEmpty() && !state.searching && query.trim().length >= 2) {
                    Text(
                        "No places found for \"${query.trim()}\" — try the town name alone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                state.searchResults.forEach { place ->
                    SheetRow(icon = null, title = place.label, subtitle = null) {
                        onPick(place)
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(
    icon: (@Composable () -> Unit)?,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(12.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
