package com.pitwall.app.ui.analytics

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.pitwall.app.ui.common.SessionJsonObjectScreen

@Composable
fun CornerClassificationScreen(navController: NavController) {
    SessionJsonObjectScreen(
        navController = navController,
        title = "Corner classification",
        subtitle = "GET /session/{id}/corner_classification",
    ) {
        cornerClassification(it, lowMax = null, medMax = null)
    }
}
