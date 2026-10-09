package com.mesmerverse.weatherballoon

import android.Manifest
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mesmerverse.weatherballoon.ui.WeatherScreen
import com.mesmerverse.weatherballoon.ui.sky
import com.mesmerverse.weatherballoon.ui.theme.WeatherBalloonTheme
import com.mesmerverse.weatherballoon.ui.theme.skyPalette

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: WeatherViewModel = viewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()

            val current = state.forecast?.current
            val palette = skyPalette(current?.let { sky(it.code) }, current?.isDay ?: true)

            // Status bar icons must flip with the sky, not the system theme.
            LaunchedEffect(palette.isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (palette.isDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                )
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted -> viewModel.onPermissionResult(granted) }

            LifecycleResumeEffect(Unit) {
                viewModel.refreshIfStale()
                onPauseOrDispose { }
            }

            WeatherBalloonTheme(palette) {
                WeatherScreen(
                    state = state,
                    palette = palette,
                    onRefresh = viewModel::refresh,
                    onToggleUnit = viewModel::toggleUnit,
                    onPickPlace = viewModel::pinPlace,
                    onFollowDevice = viewModel::followDevice,
                    onSearch = viewModel::search,
                    onToggleRadar = viewModel::toggleRadar,
                    onRetryRadar = viewModel::retryRadar,
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                )
            }
        }
    }
}
