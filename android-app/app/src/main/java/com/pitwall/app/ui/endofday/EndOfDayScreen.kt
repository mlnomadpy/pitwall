package com.pitwall.app.ui.endofday

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pitwall.app.data.remote.DebriefRequestDto
import com.pitwall.app.data.remote.NetworkModule
import com.pitwall.app.di.SessionHolder
import com.pitwall.app.data.local.SaveStore
import com.pitwall.app.ui.navigation.Routes
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndOfDayScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val api = NetworkModule.pitwallApi

    var loading by remember { mutableStateOf(true) }
    var tallyErr by remember { mutableStateOf<String?>(null) }
    var sessionCount by remember { mutableStateOf(0) }
    var totalLaps by remember { mutableStateOf(0) }
    var bestLapText by remember { mutableStateOf("--:--.--") }

    var debriefLoading by remember { mutableStateOf(false) }
    var debriefErr by remember { mutableStateOf<String?>(null) }
    var narrative by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        tallyErr = null
        try {
            val env = api.sessions(limit = 200, activeOnly = false)
            sessionCount = env.sessions.size
            totalLaps = env.sessions.sumOf { it.lapCount }
            val bests = env.sessions.mapNotNull { it.bestLapS }
            bestLapText =
                if (bests.isEmpty()) {
                    "--:--.--"
                } else {
                    val s = bests.minOrNull()!!
                    val m = (s / 60).toInt()
                    val sec = s % 60
                    "%d:%05.2f".format(m, sec)
                }
        } catch (e: Exception) {
            tallyErr = e.message
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("End of day") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                "Wrap-up tally from bridge /sessions + optional POST /coach/debrief narrative (Vue EndOfDay flow).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                loading ->
                    CircularProgressIndicator(
                        Modifier
                            .padding(24.dp),
                    )
                tallyErr != null ->
                    Text(tallyErr!!, color = MaterialTheme.colorScheme.error)
                else -> {
                    Text("Sessions: $sessionCount", style = MaterialTheme.typography.titleMedium)
                    Text("Total laps: $totalLaps", style = MaterialTheme.typography.bodyLarge)
                    Text("Best lap (bridge): $bestLapText", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Level progress: Lv ${SaveStore.activeSlot()?.level ?: "?"}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            Button(
                onClick = {
                    val sid = SessionHolder.activeSessionId
                    if (sid.isNullOrBlank()) {
                        debriefErr = "Pick an active session first (Sessions or Ghost manager)."
                        return@Button
                    }
                    debriefLoading = true
                    debriefErr = null
                    narrative = null
                    scope.launch {
                        try {
                            val json: JsonObject =
                                api.coachDebrief(
                                    DebriefRequestDto(
                                        sessionId = sid,
                                        driverId = SessionHolder.activeDriver,
                                    ),
                                )
                            narrative =
                                json["narrative_md"]?.jsonPrimitive?.content
                                    ?: json["narrative"]?.jsonPrimitive?.content
                                    ?: json.toString()
                        } catch (e: Exception) {
                            debriefErr = e.message ?: e.toString()
                        } finally {
                            debriefLoading = false
                        }
                    }
                },
                enabled = !debriefLoading,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
            ) {
                Text(if (debriefLoading) "Fetching debrief…" else "Fetch coach debrief")
            }

            debriefErr?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            narrative?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Button(
                onClick = { navController.navigate(Routes.STAGE_CLEAR) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
            ) {
                Text("Full debrief UI (stage clear)")
            }
        }
    }
}
