package com.pitwall.app.ui.analysis

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pitwall.app.di.SessionHolder
import com.pitwall.app.ui.navigation.Routes
import com.pitwall.app.ui.sessions.SessionsListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GhostManagerScreen(
    navController: NavController,
    vm: SessionsListViewModel = viewModel(),
) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var picked by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.load(limit = 50)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ghost manager") },
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
                .padding(16.dp)
                .fillMaxSize(),
        ) {
            Text(
                "Pick a session row as a ghost reference (sets active session), then compare pedal traces.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            when {
                loading ->
                    CircularProgressIndicator(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(24.dp),
                    )
                error != null ->
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                else ->
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(sessions, key = { it.sessionId }) { s ->
                            Card(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        picked = s.sessionId
                                        SessionHolder.activeSessionId = s.sessionId
                                    },
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(s.sessionId, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${s.track} · ${s.lapCount} laps · best ${s.bestLapS ?: "—"}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    if (picked == s.sessionId) {
                                        Text("Selected as active session", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
            }
            Text(
                "Active session: ${SessionHolder.activeSessionId ?: "none"}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Button(
                onClick = { navController.navigate(Routes.COMPARE) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
            ) {
                Text("Compare pedal behavior (two sessions)")
            }
            Button(
                onClick = { navController.navigate(Routes.PEDALS) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
            ) {
                Text("Pedal profile (current session)")
            }
        }
    }
}
