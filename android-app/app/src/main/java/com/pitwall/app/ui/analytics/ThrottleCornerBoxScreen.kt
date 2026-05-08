package com.pitwall.app.ui.analytics

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.pitwall.app.ui.common.SessionJsonObjectScreen

@Composable
fun ThrottleCornerBoxScreen(navController: NavController) {
    SessionJsonObjectScreen(
        navController = navController,
        title = "Throttle corner box",
        subtitle = "GET /session/{id}/throttle_corner_box",
    ) {
        throttleCornerBox(it)
    }
}
