package com.pitwall.app.ui.stage

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.pitwall.app.data.remote.compactSummary
import com.pitwall.app.data.remote.prettyJson
import com.pitwall.app.data.remote.topLevelNumericFractions
import com.pitwall.app.ui.components.pitwall.PitwallHorizontalBar
import com.pitwall.app.di.SessionHolder
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StageClearScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var debrief by remember { mutableStateOf<JsonObject?>(null) }
    var showRawDebrief by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post-session debrief") },
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
            Text(
                "ADK debrief via POST /coach/debrief (needs paddock tier). Session: ${SessionHolder.activeSessionId ?: "none"}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Button(
                onClick = {
                    showRawDebrief = false
                    val sid = SessionHolder.activeSessionId
                    if (sid.isNullOrBlank()) {
                        error = "No active session"
                        return@Button
                    }
                    scope.launch {
                        loading = true
                        error = null
                        debrief =
                            try {
                                NetworkModule.pitwallApi.coachDebrief(
                                    DebriefRequestDto(
                                        sessionId = sid,
                                        driverId = SessionHolder.activeDriver,
                                    ),
                                )
                            } catch (e: Exception) {
                                error = e.message ?: e.toString()
                                null
                            }
                        loading = false
                    }
                },
                enabled = !loading,
            ) {
                Text(if (loading) "Running…" else "Run debrief")
            }
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
                error != null ->
                    Text(
                        error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                debrief != null -> {
                    val d = debrief!!
                    Column(Modifier.padding(top = 12.dp)) {
                        val bars = d.topLevelNumericFractions().take(12)
                        if (bars.isNotEmpty()) {
                            Text(
                                "Numeric snapshot",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            bars.forEach { (k, frac) ->
                                PitwallHorizontalBar(
                                    label = k,
                                    fraction = frac,
                                    caption = "${(frac * 100).toInt()}%",
                                )
                            }
                        }
                        Text(
                            d.compactSummary(maxKeys = 80),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        TextButton(
                            onClick = { showRawDebrief = !showRawDebrief },
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(if (showRawDebrief) "Hide raw JSON" else "Show full JSON")
                        }
                        if (showRawDebrief) {
                            Text(
                                d.prettyJson(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
