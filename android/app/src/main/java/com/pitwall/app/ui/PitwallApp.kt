package com.pitwall.app.ui

import androidx.compose.runtime.*
import com.pitwall.app.ui.theme.PitwallTheme

@Composable
fun PitwallApp(viewModel: PitwallViewModel) {
    val state by viewModel.ui.collectAsState()

    PitwallTheme {
        when (state.mode) {
            AppMode.SETUP -> SetupScreen(
                uiState = state,
                onStartSession = { path, level -> viewModel.startSession(path, level) },
                onLevelChange = { viewModel.setDriverLevel(it) },
                onUseMphChange = { viewModel.setUseMph(it) },
            )
            AppMode.ON_TRACK -> OnTrackScreen(
                telemetry = state.telemetry,
                lastCoaching = state.lastCoaching,
                trackOutline = state.trackOutline,
                useMph = state.useMph,
                onEnterPaddock = { viewModel.enterPaddock() },
                onReturnToSetup = { viewModel.stopSession() },
            )
            AppMode.PADDOCK -> PaddockScreen(
                telemetry    = state.telemetry,
                lastCoaching = state.lastCoaching,
                laps         = state.laps,
                insights     = state.insights,
                insightsLoading = state.insightsLoading,
                insightsError   = state.insightsError,
                cornerStats  = state.cornerStats,
                trackOutline = state.trackOutline,
                useMph       = state.useMph,
                onReturnToTrack = { viewModel.returnToTrack() },
                onRefreshInsights = { viewModel.fetchPendingInsights() },
            )
        }
    }
}
