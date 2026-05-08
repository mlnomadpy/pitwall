package com.pitwall.app.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pitwall.app.data.remote.CornerGradeDto
import com.pitwall.app.data.remote.NetworkModule
import com.pitwall.app.data.remote.PedalBehaviorDto
import com.pitwall.app.data.remote.ScorecardEnvelopeDto
import com.pitwall.app.data.remote.SessionScorecardDto
import com.pitwall.app.data.remote.StraightLineSpeedDto
import com.pitwall.app.data.remote.encodePretty
import com.pitwall.app.di.SessionHolder
import retrofit2.HttpException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CornerMasteryScreen(navController: NavController) {
    val sid = SessionHolder.activeSessionId
    var envelope by remember { mutableStateOf<ScorecardEnvelopeDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(sid) {
        if (sid.isNullOrBlank()) {
            envelope = null
            error = null
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        envelope = null
        try {
            envelope = NetworkModule.pitwallApi.scorecard(sid)
        } catch (e: HttpException) {
            error =
                when (e.code()) {
                    404 -> "Session not analysed — run Post-session debrief first."
                    else -> e.message ?: "HTTP ${e.code()}"
                }
        } catch (e: Exception) {
            error = e.message ?: e.toString()
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Corner scorecard") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            sid.isNullOrBlank() ->
                Text(
                    "No active session.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(padding).padding(16.dp),
                )
            loading ->
                CircularProgressIndicator(
                    modifier = Modifier.padding(padding).padding(24.dp),
                )
            error != null ->
                Text(
                    error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(padding).padding(16.dp),
                )
            envelope != null -> {
                val env = envelope!!
                val errMsg = env.error
                val sc = env.scorecard
                when {
                    !errMsg.isNullOrBlank() ->
                        Text(
                            errMsg,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(padding).padding(16.dp),
                        )
                    sc == null ->
                        Text(
                            "No scorecard in bundle — run Post-session debrief first.",
                            modifier = Modifier.padding(padding).padding(16.dp),
                        )
                    else ->
                        ScorecardBody(
                            modifier = Modifier.padding(padding),
                            scorecard = sc,
                        )
                }
            }
        }
    }
}

@Composable
private fun ScorecardBody(
    modifier: Modifier = Modifier,
    scorecard: SessionScorecardDto,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    ),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Session ${scorecard.sessionGrade} · ${(scorecard.weightedTotalPct * 100).toInt()}% weighted",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Best ${scorecard.bestLapS}s · Gold ref ${scorecard.goldLapS}s · ${scorecard.nLaps} laps",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        scorecard.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        items(scorecard.corners, key = { "${it.corner}-${it.lap}" }) { corner ->
            CornerGradeCard(corner)
        }
    }
}

@Composable
private fun CornerGradeCard(corner: CornerGradeDto) {
    val tint = gradeTint(corner.grade)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "${corner.corner} · lap ${corner.lap}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                corner.grade,
                style = MaterialTheme.typography.headlineSmall,
                color = tint,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Text(
                "Δ corner ${corner.deltaTimeS}s · entry ${corner.entryDeltaKmh} · apex ${corner.apexDeltaKmh} · exit ${corner.exitDeltaKmh} km/h",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Trail brake quality ${(corner.trailBrakeQuality * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            corner.brakePointDeltaM?.let {
                Text(
                    "Brake point Δ ${it}m",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                corner.trodVoice,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (corner.timeLossAttribution.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                corner.timeLossAttribution.forEach { a ->
                    Text(
                        "${a.cause}: ${a.secondsLost}s — ${a.detail}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun gradeTint(grade: String): Color {
    return when {
        grade.startsWith("A") -> MaterialTheme.colorScheme.primary
        grade.startsWith("B") -> MaterialTheme.colorScheme.secondary
        grade.startsWith("C") -> MaterialTheme.colorScheme.tertiary
        grade.startsWith("D") -> MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
        else -> MaterialTheme.colorScheme.error
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PedalProfileScreen(navController: NavController) {
    SessionJsonDetailScreen(navController, "Pedal profile") { sid ->
        encodePretty(
            PedalBehaviorDto.serializer(),
            NetworkModule.pitwallApi.pedalBehavior(sid),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StraightsAndSpeedScreen(navController: NavController) {
    SessionJsonDetailScreen(navController, "Straight-line speed") { sid ->
        encodePretty(
            StraightLineSpeedDto.serializer(),
            NetworkModule.pitwallApi.straightLineSpeed(sid),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionJsonDetailScreen(
    navController: NavController,
    title: String,
    fetch: suspend (String) -> String,
) {
    val sid = SessionHolder.activeSessionId
    var body by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(sid, title) {
        if (sid.isNullOrBlank()) {
            body = null
            error = null
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            body = fetch(sid)
        } catch (e: Exception) {
            error = e.message ?: e.toString()
            body = null
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(16.dp),
        ) {
            when {
                sid.isNullOrBlank() ->
                    Text(
                        "No active session.",
                        color = MaterialTheme.colorScheme.error,
                    )
                loading -> CircularProgressIndicator()
                error != null ->
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                body != null ->
                    Text(
                        body!!,
                        style = MaterialTheme.typography.bodySmall,
                    )
            }
        }
    }
}
