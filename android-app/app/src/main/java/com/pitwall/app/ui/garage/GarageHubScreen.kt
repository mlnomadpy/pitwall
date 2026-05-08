package com.pitwall.app.ui.garage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pitwall.app.data.local.SaveStore
import com.pitwall.app.di.SessionHolder
import com.pitwall.app.ui.components.pitwall.PitwallSectionDivider
import com.pitwall.app.ui.components.pitwall.PitwallSectionHeader
import com.pitwall.app.ui.components.pitwall.PitwallTileCard
import com.pitwall.app.ui.navigation.Routes

private data class GarageTile(
    val title: String,
    val subtitle: String,
    val route: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageHubScreen(navController: NavController) {
    LaunchedEffect(Unit) {
        if (SaveStore.activeSlot() == null) {
            navController.navigate(Routes.SAVE) {
                launchSingleTop = true
            }
        }
    }

    val primary =
        listOf(
            GarageTile("TRACK", "GO RACING · PRE-BRIEF", Routes.BRIEFING),
            GarageTile("PIT STALL", "CONNECT · TUNE", Routes.PIT_STALL),
            GarageTile("TRAINER CARD", "STATS · MEDALS", Routes.TRAINER_CARD),
            GarageTile("ANALYSIS", "LAPS · CORNERS", Routes.ANALYSIS),
            GarageTile("COACHES", "ROSTER · AFFINITY", Routes.COACH_SELECT),
            GarageTile("HIGH SCORES", "GLOBAL RANKING", Routes.LEADERBOARD),
            GarageTile("QUEST LOG", "GOALS · CONTRACTS", Routes.QUESTS),
            GarageTile("CAR SETUP", "AERO · BRAKES · DIFF", Routes.CAR_SETUP),
        )

    val shortcuts =
        listOf(
            GarageTile("SETTINGS", "AUDIO · DISPLAY · INPUT", Routes.SETTINGS),
            GarageTile("CALIBRATION", "INPUT · DEADZONES", Routes.CALIBRATION),
            GarageTile("HARDWARE", "SIGNAL REGISTRY", Routes.HARDWARE),
            GarageTile("BRIDGE SESSIONS", "LIVE SESSION ROWS", Routes.BRIDGE_SESSIONS),
            GarageTile("HUD", "ON-TRACK · CUES SSE", Routes.HUD),
            GarageTile("ASK COACH", "PADDOCK Q&A", Routes.COACH_ASK),
            GarageTile("NOTIFICATIONS", "INBOX SSE", Routes.NOTIFICATIONS),
            GarageTile("END OF DAY", "TALLY · DEBRIEF", Routes.END_OF_DAY),
        )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Garage", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            SaveStore.activeSlot()?.driverName?.let { "Driver · $it" }
                                ?: "No driver",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    ),
            )
        },
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val wide = maxWidth >= 600.dp
            if (wide) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(2) }) {
                        PitwallSectionHeader("Primary")
                    }
                    itemsIndexed(primary) { i, t ->
                        PitwallTileCard(
                            title = t.title,
                            subtitle = t.subtitle,
                            onClick = { navController.navigate(t.route) },
                            emphasized = i == 0,
                            entranceIndex = i,
                        )
                    }
                    item(span = { GridItemSpan(2) }) {
                        PitwallSectionDivider()
                    }
                    item(span = { GridItemSpan(2) }) {
                        PitwallSectionHeader("Shortcuts")
                    }
                    itemsIndexed(shortcuts) { i, t ->
                        PitwallTileCard(
                            title = t.title,
                            subtitle = t.subtitle,
                            onClick = { navController.navigate(t.route) },
                            entranceIndex = primary.size + i,
                        )
                    }
                    item(span = { GridItemSpan(2) }) {
                        Text(
                            "Session · ${SessionHolder.activeSessionId ?: "none"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                        )
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PitwallSectionHeader("Primary")
                    primary.forEachIndexed { i, t ->
                        PitwallTileCard(
                            title = t.title,
                            subtitle = t.subtitle,
                            onClick = { navController.navigate(t.route) },
                            emphasized = i == 0,
                            entranceIndex = i,
                        )
                    }
                    PitwallSectionDivider()
                    PitwallSectionHeader("Shortcuts")
                    shortcuts.forEachIndexed { i, t ->
                        PitwallTileCard(
                            title = t.title,
                            subtitle = t.subtitle,
                            onClick = { navController.navigate(t.route) },
                            entranceIndex = primary.size + i,
                        )
                    }
                    Text(
                        "Session · ${SessionHolder.activeSessionId ?: "none"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                    )
                }
            }
        }
    }
}
