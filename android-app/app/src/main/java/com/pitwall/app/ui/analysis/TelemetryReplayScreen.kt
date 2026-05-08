package com.pitwall.app.ui.analysis

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pitwall.app.data.remote.NetworkModule
import com.pitwall.app.di.SessionHolder
import com.pitwall.app.ui.components.pitwall.MiniSparkline
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelemetryReplayScreen(navController: NavController) {
    val sid = SessionHolder.activeSessionId
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var rows by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sid) {
        loadError = null
        rows = emptyList()
        index = 0
        progress = 0f
        playing = false
        if (sid.isNullOrBlank()) return@LaunchedEffect
        loading = true
        try {
            val json =
                NetworkModule.pitwallApi.sessionSignalsGet(
                    sessionId = sid,
                    names = "speed_ms,throttle_pct",
                    axis = "time",
                    interp = "hold",
                    rateHz = 10.0,
                    tFrom = null,
                    tTo = null,
                )
            val arr = json["rows"]?.jsonArray ?: JsonArray(emptyList())
            rows = arr.mapNotNull { el -> el as? JsonObject }
            index = 0
        } catch (e: Exception) {
            loadError = e.message ?: e.toString()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(playing, rows.size) {
        while (playing && rows.isNotEmpty()) {
            delay(40)
            if (index >= rows.lastIndex) {
                playing = false
                break
            }
            index++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Telemetry replay")
                        Text(
                            if (rows.isNotEmpty()) {
                                "Playing aligned samples from /session/…/signals"
                            } else {
                                "Select a session — scrubber is demo until signals load"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val speedSeries =
            remember(rows) {
                rows.mapNotNull { r -> r["speed_ms"]?.jsonPrimitive?.doubleOrNull }
            }
        val throttleSeries =
            remember(rows) {
                rows.mapNotNull { r -> r["throttle_pct"]?.jsonPrimitive?.doubleOrNull }
            }
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            when {
                sid.isNullOrBlank() ->
                    Text(
                        "No active session. Pick one from Sessions, then return here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                loading ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 12.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("Loading signals…", style = MaterialTheme.typography.bodyMedium)
                    }
                loadError != null ->
                    Text(
                        loadError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
            }

            if (speedSeries.size >= 2 || throttleSeries.size >= 2) {
                Text(
                    "Series overview (full pull)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (speedSeries.size >= 2) {
                    Text("Speed (m/s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MiniSparkline(values = speedSeries, usePrimary = true, modifier = Modifier.padding(bottom = 12.dp))
                }
                if (throttleSeries.size >= 2) {
                    Text("Throttle %", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MiniSparkline(values = throttleSeries, modifier = Modifier.padding(bottom = 12.dp))
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                IconButton(
                    onClick = { playing = !playing },
                    enabled = rows.isNotEmpty(),
                ) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                    )
                }
                Text(
                    when {
                        rows.isEmpty() && !loading -> "Paused (no rows)"
                        playing -> "Playing"
                        else -> "Paused"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (rows.isNotEmpty()) {
                Slider(
                    value = index.toFloat(),
                    onValueChange = {
                        index = it.toInt().coerceIn(0, rows.lastIndex)
                        playing = false
                    },
                    valueRange = 0f..rows.lastIndex.toFloat(),
                    steps = if (rows.lastIndex > 0) rows.lastIndex - 1 else 0,
                    modifier = Modifier.fillMaxWidth(),
                )
                val row = rows.getOrNull(index)
                val speed = row?.get("speed_ms")?.jsonPrimitive?.doubleOrNull
                val thr = row?.get("throttle_pct")?.jsonPrimitive?.doubleOrNull
                val t = row?.get("time")?.jsonPrimitive?.doubleOrNull
                    ?: row?.get("t")?.jsonPrimitive?.doubleOrNull
                Text(
                    buildString {
                        append("Sample ${index + 1} / ${rows.size}")
                        if (t != null) append(" · t=${String.format("%.3f", t)}s")
                        if (speed != null) append(" · speed_ms=${String.format("%.1f", speed)}")
                        if (thr != null) append(" · throttle=${String.format("%.0f", thr)}%")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Slider(
                    value = progress,
                    onValueChange = { progress = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Progress ${(progress * 100).toInt()}% · demo scrubber (no session rows yet)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = {
                    progress = 0f
                    index = 0
                    playing = false
                },
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Reset")
            }
        }
    }
}
