package com.pitwall.app.ui.analysis

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.pitwall.app.data.remote.PedalBehaviorDto
import com.pitwall.app.data.remote.encodePretty
import com.pitwall.app.di.SessionHolder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var sidA by remember { mutableStateOf(SessionHolder.activeSessionId.orEmpty()) }
    var sidB by remember { mutableStateOf("") }
    var textA by remember { mutableStateOf<String?>(null) }
    var textB by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Compare sessions")
                        Text(
                            "Pedal behavior · GET /session/{id}/pedal_behavior",
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
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(16.dp),
        ) {
            OutlinedTextField(
                value = sidA,
                onValueChange = { sidA = it },
                label = { Text("Session A") },
                modifier = Modifier.padding(bottom = 8.dp),
                singleLine = true,
            )
            OutlinedTextField(
                value = sidB,
                onValueChange = { sidB = it },
                label = { Text("Session B") },
                modifier = Modifier.padding(bottom = 12.dp),
                singleLine = true,
            )
            Button(
                onClick = {
                    if (sidA.isBlank() || sidB.isBlank()) {
                        error = "Enter both session IDs"
                        return@Button
                    }
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            textA =
                                encodePretty(
                                    PedalBehaviorDto.serializer(),
                                    NetworkModule.pitwallApi.pedalBehavior(sidA.trim()),
                                )
                            textB =
                                encodePretty(
                                    PedalBehaviorDto.serializer(),
                                    NetworkModule.pitwallApi.pedalBehavior(sidB.trim()),
                                )
                        } catch (e: Exception) {
                            error = e.message ?: e.toString()
                            textA = null
                            textB = null
                        }
                        loading = false
                    }
                },
                enabled = !loading,
            ) {
                Text(if (loading) "Loading…" else "Compare pedal behavior")
            }
            Spacer(Modifier.height(16.dp))
            when {
                loading -> CircularProgressIndicator()
                error != null ->
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                textA != null ->
                    Card(modifier = Modifier.padding(bottom = 12.dp)) {
                        Text(
                            "Session A",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(12.dp),
                        )
                        Text(
                            textA!!,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
            }
            if (textB != null) {
                Card {
                    Text(
                        "Session B",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(12.dp),
                    )
                    Text(
                        textB!!,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
