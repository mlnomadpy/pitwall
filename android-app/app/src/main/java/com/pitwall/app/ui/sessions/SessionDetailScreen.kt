package com.pitwall.app.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pitwall.app.di.SessionHolder
import com.pitwall.app.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: String,
    navController: NavController,
    vm: SessionDetailViewModel = viewModel(),
) {
    val detail by vm.detail.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val bridgeBusy by vm.bridgeBusy.collectAsStateWithLifecycle()
    val bridgeMessage by vm.bridgeMessage.collectAsStateWithLifecycle()

    LaunchedEffect(sessionId) {
        if (sessionId.isBlank()) return@LaunchedEffect
        SessionHolder.activeSessionId = sessionId
        vm.load(sessionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                sessionId.isBlank() ->
                    Text("Missing session id.", color = MaterialTheme.colorScheme.error)
                loading -> CircularProgressIndicator()
                error != null ->
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                detail != null -> {
                    val d = detail!!
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(d.session.sessionId, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${d.session.driver} · ${d.session.track} · ${d.lapCount} laps · best ${d.bestLapS ?: "—"}s",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text("Laps", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                        d.laps.take(20).forEach { lap ->
                            Text(
                                "Lap ${lap.lapNumber}: ${lap.lapTimeS ?: "—"}s",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (d.laps.size > 20) {
                            Text("… ${d.laps.size} total", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = { navController.navigate(Routes.LAP_TIMES) },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text("Lap time table")
                        }
                        Button(onClick = { navController.navigate(Routes.LAP_DISTRIBUTION) }) {
                            Text("Lap time distribution")
                        }
                        Button(onClick = { navController.navigate(Routes.INSIGHTS) }) {
                            Text("Insights (bursts)")
                        }
                        Button(onClick = { navController.navigate(Routes.CORNERS) }) {
                            Text("Corner scorecard")
                        }
                        Button(onClick = { navController.navigate(Routes.PEDALS) }) {
                            Text("Pedal behavior")
                        }
                        Button(onClick = { navController.navigate(Routes.STRAIGHTS) }) {
                            Text("Straight-line speed")
                        }
                        Button(onClick = { navController.navigate(Routes.STAGE_CLEAR) }) {
                            Text("Post-session debrief")
                        }
                        Button(onClick = { navController.navigate(Routes.COACH_ASK) }) {
                            Text("Ask coach")
                        }
                        Button(onClick = { navController.navigate(Routes.NOTIFICATIONS) }) {
                            Text("Notifications")
                        }
                        Text(
                            "Bridge tools",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        Text(
                            "Sync preview can be large; Parquet saves under app cache.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledTonalButton(
                            onClick = { vm.fetchSyncPreview(d.session.sessionId) },
                            enabled = !bridgeBusy,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text("Preview telemetry + video sync JSON")
                        }
                        FilledTonalButton(
                            onClick = { vm.exportParquet(d.session.sessionId) },
                            enabled = !bridgeBusy,
                        ) {
                            Text("Export telemetry Parquet")
                        }
                        FilledTonalButton(
                            onClick = { vm.resetBursts() },
                            enabled = !bridgeBusy,
                        ) {
                            Text("Reset burst accumulator")
                        }
                        if (bridgeBusy) {
                            CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                        }
                        bridgeMessage?.let { msg ->
                            Text(
                                msg,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
