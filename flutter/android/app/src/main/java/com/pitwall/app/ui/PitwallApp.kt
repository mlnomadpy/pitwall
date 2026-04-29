package com.pitwall.app.ui

import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
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
            )
            AppMode.ON_TRACK -> OnTrackScreen(
                telemetry = state.telemetry,
                lastCoaching = state.lastCoaching,
                onEnterPaddock = { viewModel.enterPaddock() },
                onReturnToSetup = { viewModel.stopSession() },
            )
            AppMode.PADDOCK -> PaddockScreen(
                telemetry = state.telemetry,
                lastCoaching = state.lastCoaching,
                onReturnToTrack = { viewModel.returnToTrack() },
            )
        }
    }
}
