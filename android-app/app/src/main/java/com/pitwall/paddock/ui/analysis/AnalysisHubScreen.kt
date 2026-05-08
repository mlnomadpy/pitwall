package com.pitwall.paddock.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitwall.paddock.ui.components.CrtOverlay
import com.pitwall.paddock.ui.components.PitwallTopBar
import com.pitwall.paddock.ui.garage.GarageTile
import com.pitwall.paddock.ui.garage.GarageTileItem
import com.pitwall.paddock.ui.theme.ColorInk

val analysisTiles = listOf(
    GarageTile("LAP TIMES", Icons.Default.Timer, "lap_times"),
    GarageTile("CORNERS", Icons.Default.Timeline, "corner_mastery"),
    GarageTile("PEDALS", Icons.Default.Speed, "pedal_profile"),
    GarageTile("COMPARISON", Icons.Default.CompareArrows, "comparison"),
    GarageTile("EVOLUTION", Icons.Default.TrendingUp, "driver_evolution"),
    GarageTile("TRACK MAP", Icons.Default.Map, "historical_track_map"),
    GarageTile("INSIGHTS", Icons.Default.Lightbulb, "insights"),
    GarageTile("HISTORY", Icons.Default.History, "session_history")
)

@Composable
fun AnalysisHubScreen(
    onNavigate: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorInk)
    ) {
        CrtOverlay(modifier = Modifier.fillMaxSize())

        Column(modifier = Modifier.fillMaxSize()) {
            PitwallTopBar(title = "ANALYSIS HUB")

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(analysisTiles) { _, tile ->
                    GarageTileItem(
                        tile = tile,
                        isFocused = false, // No cursor needed for pure grid
                        onClick = { onNavigate(tile.route) }
                    )
                }
            }
        }
    }
}
