package com.pitwall.app.ui.analysis

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.pitwall.app.data.remote.NetworkModule
import com.pitwall.app.data.remote.prettyJson
import com.pitwall.app.di.SessionHolder
import kotlinx.coroutines.launch

private enum class SqlConsoleTab(
    val label: String,
) {
    BRIDGE_PROBES("Bridge probes"),
    DUCKDB_WASM("DuckDB Wasm"),
}

private enum class ProbeMode(
    val label: String,
) {
    SIGNALS("Signals"),
    LAPS("Laps"),
    CAPABILITIES("Capabilities"),
    REGISTRY("ADR-015 registry"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SqlConsoleScreen(navController: NavController) {
    val sid = SessionHolder.activeSessionId
    val scope = rememberCoroutineScope()
    var consoleTab by remember { mutableStateOf(SqlConsoleTab.BRIDGE_PROBES) }
    var mode by remember { mutableStateOf(ProbeMode.SIGNALS) }
    var names by remember { mutableStateOf("throttle_pct,brake_bar") }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SQL / signals / laps")
                        Text(
                            "PWA parity: DuckDB-Wasm tab · Bridge REST probes",
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
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SqlConsoleTab.entries.forEach { tab ->
                    FilterChip(
                        selected = consoleTab == tab,
                        onClick = {
                            consoleTab = tab
                            result = null
                            error = null
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
            Text(
                "Session: ${sid ?: "none"}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
            when (consoleTab) {
                SqlConsoleTab.DUCKDB_WASM -> {
                    Text(
                        "DuckDB-Wasm loads `@duckdb/duckdb-wasm` from jsDelivr (same stacks as the Vue PWA). Mixed HTTP(S) is enabled so the page can fetch Parquet from your bridge URL.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    DuckDbWasmConsole(
                        sessionId = sid,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                    )
                }
                SqlConsoleTab.BRIDGE_PROBES -> {
                    Text(
                        "Bridge-backed probes — aligned signals, lap rows, capabilities, or the full ADR-015 registry.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                    ) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ProbeMode.entries.forEach { m ->
                                FilterChip(
                                    selected = mode == m,
                                    onClick = {
                                        mode = m
                                        result = null
                                        error = null
                                    },
                                    label = { Text(m.label) },
                                )
                            }
                        }
                        when (mode) {
                            ProbeMode.SIGNALS -> {
                                OutlinedTextField(
                                    value = names,
                                    onValueChange = { names = it },
                                    label = { Text("Comma-separated signal names") },
                                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                                    singleLine = false,
                                    minLines = 2,
                                )
                            }
                            ProbeMode.LAPS -> {
                                Text(
                                    "Uses GET /laps — optional filter when a session is active.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                                )
                            }
                            ProbeMode.CAPABILITIES -> {
                                Text(
                                    "Uses GET /session/{id}/capabilities — requires active session.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                                )
                            }
                            ProbeMode.REGISTRY -> {
                                Text(
                                    "Uses GET /signals/registry — full catalog (can be large).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                                )
                            }
                        }
                        Button(
                            onClick = {
                                when (mode) {
                                    ProbeMode.SIGNALS -> {
                                        if (sid.isNullOrBlank()) {
                                            error = "Select a session first."
                                            return@Button
                                        }
                                        scope.launch {
                                            loading = true
                                            error = null
                                            try {
                                                val json =
                                                    NetworkModule.pitwallApi.sessionSignalsGet(
                                                        sessionId = sid,
                                                        names = names.trim(),
                                                        axis = "time",
                                                        interp = "hold",
                                                        rateHz = 5.0,
                                                        tFrom = null,
                                                        tTo = null,
                                                    )
                                                result = json.prettyJson()
                                            } catch (e: Exception) {
                                                error = e.message ?: e.toString()
                                                result = null
                                            } finally {
                                                loading = false
                                            }
                                        }
                                    }
                                    ProbeMode.LAPS -> {
                                        scope.launch {
                                            loading = true
                                            error = null
                                            try {
                                                val json =
                                                    NetworkModule.pitwallApi.laps(
                                                        sessionId = sid,
                                                        limit = 40,
                                                    )
                                                result = json.prettyJson()
                                            } catch (e: Exception) {
                                                error = e.message ?: e.toString()
                                                result = null
                                            } finally {
                                                loading = false
                                            }
                                        }
                                    }
                                    ProbeMode.CAPABILITIES -> {
                                        if (sid.isNullOrBlank()) {
                                            error = "Select a session first."
                                            return@Button
                                        }
                                        scope.launch {
                                            loading = true
                                            error = null
                                            try {
                                                val json =
                                                    NetworkModule.pitwallApi.sessionCapabilities(sid)
                                                result = json.prettyJson()
                                            } catch (e: Exception) {
                                                error = e.message ?: e.toString()
                                                result = null
                                            } finally {
                                                loading = false
                                            }
                                        }
                                    }
                                    ProbeMode.REGISTRY -> {
                                        scope.launch {
                                            loading = true
                                            error = null
                                            try {
                                                val json = NetworkModule.pitwallApi.signalsRegistry()
                                                result = json.prettyJson()
                                            } catch (e: Exception) {
                                                error = e.message ?: e.toString()
                                                result = null
                                            } finally {
                                                loading = false
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !loading &&
                                when (mode) {
                                    ProbeMode.SIGNALS -> !sid.isNullOrBlank()
                                    ProbeMode.LAPS -> true
                                    ProbeMode.CAPABILITIES -> !sid.isNullOrBlank()
                                    ProbeMode.REGISTRY -> true
                                },
                        ) {
                            Text(
                                when (mode) {
                                    ProbeMode.SIGNALS -> "Fetch aligned samples"
                                    ProbeMode.LAPS -> "Fetch lap rows"
                                    ProbeMode.CAPABILITIES -> "Fetch capabilities"
                                    ProbeMode.REGISTRY -> "Fetch signal registry"
                                },
                            )
                        }
                        when {
                            loading ->
                                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
                            error != null ->
                                Text(
                                    error!!,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            result != null ->
                                Text(
                                    result!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                        }
                    }
                }
            }
        }
    }
}
