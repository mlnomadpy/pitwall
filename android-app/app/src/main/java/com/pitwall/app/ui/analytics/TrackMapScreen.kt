package com.pitwall.app.ui.analytics

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.pitwall.app.ui.common.SessionJsonObjectScreen

@Composable
fun TrackMapScreen(navController: NavController) {
    SessionJsonObjectScreen(
        navController = navController,
        title = "Track map overlay",
        subtitle = "GET /session/{id}/map",
    ) {
        sessionMap(it)
    }
}
