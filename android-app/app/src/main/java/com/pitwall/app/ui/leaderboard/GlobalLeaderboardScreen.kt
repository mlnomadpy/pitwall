package com.pitwall.app.ui.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalLeaderboardScreen(
    navController: NavController,
    vm: LeaderboardViewModel = viewModel(),
) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val source by vm.source.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val hint by vm.errorHint.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("High scores")
                        Text(
                            when (source) {
                                LeaderboardSource.Bridge ->
                                    "Live · GET /laps + /sessions (best lap per session)"
                                LeaderboardSource.Mock ->
                                    "Demo ladder · bridge returned no lap rows"
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
                actions = {
                    IconButton(
                        onClick = { vm.refresh() },
                        enabled = !loading,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh from bridge")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
        ) {
            hint?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (source == LeaderboardSource.Mock) {
                Text(
                    "When DuckDB has laps, we rank best lap per session and prefer driver names from GET /sessions. No shared global leaderboard API exists yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            FilledTonalButton(
                onClick = { vm.refresh() },
                enabled = !loading,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
            ) {
                Text(if (loading) "Refreshing…" else "Refresh from bridge")
            }
            if (loading && entries.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("RK", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                    Text("NAME", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                    Text("CAR", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                    Text("TIME", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(entries, key = { "${it.rank}-${it.name}-${it.timeText}" }) { e ->
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${e.rank}",
                                    fontFamily = FontFamily.Monospace,
                                    color =
                                        if (e.isHighlighted) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                )
                                Text(e.name, fontFamily = FontFamily.Monospace)
                                Text(
                                    e.carOrMeta,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(e.timeText, fontFamily = FontFamily.Monospace)
                            }
                            Text(
                                e.trackOrSession,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                            )
                        }
                    }
                }
                if (source == LeaderboardSource.Mock) {
                    Text(
                        "INSERT COIN",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier =
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 16.dp),
                    )
                }
            }
        }
    }
}
