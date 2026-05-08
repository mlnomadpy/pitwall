package com.pitwall.app.ui.analytics

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.pitwall.app.ui.common.SessionJsonObjectScreen

@Composable
fun TrackAtlasScreen(navController: NavController) {
    SessionJsonObjectScreen(
        navController = navController,
        title = "Session corners",
        subtitle = "GET /session/{id}/corners",
    ) {
        sessionCornersAggregate(it)
    }
}
