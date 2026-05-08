package com.pitwall.app.ui.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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

private data class HubModule(
    val title: String,
    val subtitle: String,
    val route: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisHubScreen(navController: NavController) {
    val slot = SaveStore.activeSlot()
    val bestLabel =
        slot?.bestLapBySession?.values?.minOrNull()?.let { s ->
            val m = (s / 60).toInt()
            val sec = s % 60
            "%d:%05.2f".format(m, sec)
        } ?: "—"
    val lapEstimate =
        slot?.sessions?.sumOf { it.lapCount } ?: 0

    val core =
        listOf(
            HubModule("LAP TIMES HALL", "$lapEstimate LAPS (SAVE)", Routes.LAP_TIMES),
            HubModule("CORNER MASTERY", "SCORECARD A–F", Routes.CORNERS),
            HubModule("STRAIGHTS & SPEED", "TOP SPEED / STRAIGHT", Routes.STRAIGHTS),
            HubModule("TRACK MAP", "GPS · MARKERS", Routes.TRACK_WALK),
            HubModule("TRACK REFERENCE", "MARKERS · WEATHER", Routes.TRACK_REFERENCE),
            HubModule("DRIVER EVOLUTION", "MULTI-SESSION", Routes.EVOLUTION),
            HubModule("PEDAL PROFILE", "THROTTLE · BRAKE", Routes.PEDALS),
            HubModule("GHOST DATA", "PICK SESSION · OVERLAY", Routes.GHOSTS),
            HubModule("VCR REPLAY", "SIGNAL TIMELINE", Routes.REPLAY),
        )

    val extended =
        listOf(
            HubModule("DEBRIEF BUNDLE", "POST COACH/DEBRIEF", Routes.ANALYSIS_BUNDLE),
            HubModule("INSIGHTS", "COACHING GAPS", Routes.INSIGHTS),
            HubModule("LAP DISTRIBUTION", "BOX PLOT", Routes.LAP_DISTRIBUTION),
            HubModule("SECTOR TIMES", "S1 · S2 · S3", Routes.SECTOR_TIMES),
            HubModule("SESSION CLIPS", "VIDEO CUTS", Routes.SESSION_CLIPS),
            HubModule("BRAKE / EXIT ACCEL", "SCATTER", Routes.BRAKE_ACCEL),
            HubModule("THROTTLE CORNER BOX", "PER CORNER", Routes.THROTTLE_CORNER_BOX),
            HubModule("CORNER CLASSIFICATION", "SPEED BANDS", Routes.CORNER_CLASSIFICATION),
            HubModule("SESSION CORNERS", "AGGREGATES", Routes.ATLAS),
            HubModule("COMPARE PEDALS", "TWO SESSIONS", Routes.COMPARE),
            HubModule("ASK COACH", "Q&A", Routes.COACH_ASK),
            HubModule("CONCEPTS", "BENTLEY CATALOG", Routes.COACH_CONCEPTS),
            HubModule("NOTIFICATIONS", "SSE INBOX", Routes.NOTIFICATIONS),
        )

    val sid = SessionHolder.activeSessionId
    val hasSession = !sid.isNullOrBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Analysis hall", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            if (hasSession) "Session $sid" else "No active session",
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (hasSession) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
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
            val wide = maxWidth >= 720.dp
            if (wide) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Card(
                        modifier =
                            Modifier
                                .widthIn(max = 380.dp)
                                .fillMaxHeight(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            ),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            PitwallSectionHeader("Latest overview")
                            Text(
                                "Best lap (save)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                bestLabel,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "Track · ${slot?.preferredTrack?.uppercase() ?: "—"}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            Text(
                                "Sessions in save · ${slot?.sessions?.size ?: 0}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Coach · ${slot?.preferredCoach?.uppercase() ?: "—"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            if (!hasSession) {
                                Text(
                                    "Select a bridge session (Save → Bridge sessions or Ghost manager) to unlock modules.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 16.dp),
                                )
                            }
                        }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(span = { GridItemSpan(2) }) {
                            PitwallSectionHeader("Modules (PWA)")
                        }
                        itemsIndexed(core) { i, m ->
                            PitwallTileCard(
                                title = m.title,
                                subtitle = m.subtitle,
                                onClick = { navController.navigate(m.route) },
                                enabled = hasSession,
                                emphasized = i == 0 && hasSession,
                                entranceIndex = i,
                            )
                        }
                        item(span = { GridItemSpan(2) }) {
                            PitwallSectionDivider()
                        }
                        item(span = { GridItemSpan(2) }) {
                            PitwallSectionHeader("More · bridge bundle")
                        }
                        itemsIndexed(extended) { i, m ->
                            PitwallTileCard(
                                title = m.title,
                                subtitle = m.subtitle,
                                onClick = { navController.navigate(m.route) },
                                entranceIndex = core.size + i,
                            )
                        }
                        item(span = { GridItemSpan(2) }) {
                            PitwallTileCard(
                                title = "SQL · SIGNALS PROBE",
                                subtitle = "SPACE IN PWA — /signals",
                                onClick = { navController.navigate(Routes.SQL) },
                                entranceIndex = core.size + extended.size,
                            )
                        }
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
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            ),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Overview", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Best lap $bestLabel · ${slot?.preferredTrack?.uppercase() ?: "—"}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (!hasSession) {
                                Text(
                                    "No active session — pick one under Bridge sessions.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                    PitwallSectionHeader("Modules (PWA)")
                    core.forEachIndexed { i, m ->
                        PitwallTileCard(
                            title = m.title,
                            subtitle = m.subtitle,
                            onClick = { navController.navigate(m.route) },
                            enabled = hasSession,
                            emphasized = i == 0 && hasSession,
                            entranceIndex = i,
                        )
                    }
                    PitwallSectionDivider()
                    PitwallSectionHeader("More · bridge bundle")
                    extended.forEachIndexed { i, m ->
                        PitwallTileCard(
                            title = m.title,
                            subtitle = m.subtitle,
                            onClick = { navController.navigate(m.route) },
                            entranceIndex = core.size + i,
                        )
                    }
                    PitwallTileCard(
                        title = "SQL · SIGNALS PROBE",
                        subtitle = "SPACE · /signals",
                        onClick = { navController.navigate(Routes.SQL) },
                        entranceIndex = core.size + extended.size,
                    )
                    Text(
                        "Tip: set active session before gated modules.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                    )
                }
            }
        }
    }
}
