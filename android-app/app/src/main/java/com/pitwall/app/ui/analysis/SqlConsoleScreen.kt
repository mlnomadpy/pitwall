package com.pitwall.app.ui.analysis

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SqlConsoleScreen(navController: NavController) {
    val sid = SessionHolder.activeSessionId
    val scope = rememberCoroutineScope()
    var names by remember { mutableStateOf("throttle_pct,brake_bar") }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SQL / signals probe")
                        Text(
                            "PWA runs DuckDB in-browser; here we call GET /session/…/signals",
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
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(16.dp),
        ) {
            Text(
                "Full SQL against telemetry requires the Vue PWA’s embedded DuckDB or exporting Parquet from the bridge. Use this panel to pull aligned signal samples for the active session.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Text(
                "Session: ${sid ?: "none"}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = names,
                onValueChange = { names = it },
                label = { Text("Comma-separated signal names") },
                modifier = Modifier.padding(bottom = 8.dp),
                singleLine = false,
                minLines = 2,
            )
            Button(
                onClick = {
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
                },
                enabled = !loading && !sid.isNullOrBlank(),
            ) {
                Text("Fetch aligned samples")
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
