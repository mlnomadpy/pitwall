package com.pitwall.app.ui.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pitwall.app.di.SessionHolder
import com.pitwall.app.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsListScreen(
    navController: NavController,
    vm: SessionsListViewModel = viewModel(),
    sessionLimit: Int = 50,
    screenTitle: String = "Sessions",
) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()

    LaunchedEffect(sessionLimit) {
        vm.load(limit = sessionLimit)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
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
            Text(
                "Open a session to set the active session for HUD + analytics.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            when {
                loading ->
                    CircularProgressIndicator(
                        modifier =
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(24.dp),
                    )
                error != null ->
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                else ->
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sessions, key = { it.sessionId }) { s ->
                            Card(
                                modifier =
                                    Modifier.clickable {
                                        SessionHolder.activeSessionId = s.sessionId
                                        navController.navigate("session/${s.sessionId}")
                                    },
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(s.sessionId, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${s.driver} · ${s.track} · ${s.lapCount} laps",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    s.bestLapS?.let {
                                        Text("Best: ${it}s", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }
}
