package com.pitwall.app.ui.analytics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pitwall.app.di.SessionHolder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScoreScreen(
    navController: NavController,
    vm: SessionScoreViewModel = viewModel(),
) {
    val result by vm.result.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    var focus by remember { mutableStateOf("") }
    val sid = SessionHolder.activeSessionId

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Session grade")
                        Text(
                            "POST /score · Gemini (503 if no API key)",
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
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "Session: ${sid ?: "none"} · driver level from active save slot.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            OutlinedTextField(
                value = focus,
                onValueChange = { focus = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Focus (optional)") },
                placeholder = { Text("e.g. braking, consistency") },
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { vm.gradeSession(focus) },
                enabled = !loading && !sid.isNullOrBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (loading) "Grading…" else "Grade session")
            }
            when {
                loading ->
                    CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
                error != null ->
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                result != null -> {
                    val r = result!!
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            ),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "${r.score}",
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                r.why,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            r.model?.let {
                                Text(
                                    "Model · $it",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
