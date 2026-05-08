package com.pitwall.app.ui.analytics

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LapTimesScreen(
    navController: NavController,
    vm: LapTimesViewModel = viewModel(),
) {
    val table by vm.table.collectAsStateWithLifecycle()
    val ideal by vm.ideal.collectAsStateWithLifecycle()
    val idealError by vm.idealError.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val sid = SessionHolder.activeSessionId

    LaunchedEffect(sid) {
        if (!sid.isNullOrBlank()) {
            vm.load(sid)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lap times") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            sid.isNullOrBlank() ->
                Text(
                    "No active session. Pick one under Sessions.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(padding).padding(16.dp),
                )
            loading ->
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .padding(padding)
                            .padding(24.dp),
                )
            error != null ->
                Text(
                    error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(padding).padding(16.dp),
                )
            table != null -> {
                val t = table!!
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                ) {
                    item {
                        Surface(
                            tonalElevation = 2.dp,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "Best lap ${t.bestLapNumber}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    "${t.bestLapS}s · ${t.lapCount} laps total",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    ideal?.let { idl ->
                        item {
                            Surface(
                                tonalElevation = 1.dp,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        "Ideal lap (sum of best sectors)",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                    Text(
                                        "${idl.idealLapS}s vs best actual ${idl.bestActualLapS}s · gain potential ${idl.gainPotentialS}s",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    idl.bestSectors.take(4).forEach { s ->
                                        Text(
                                            "${s.name}: ${s.timeS}s (lap ${s.fromLap})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                    if (idl.bestSectors.size > 4) {
                                        Text(
                                            "… +${idl.bestSectors.size - 4} sectors",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    idealError?.let { ie ->
                        item {
                            Text(
                                "Ideal lap: $ie",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    items(t.laps, key = { it.lapNumber }) { lap ->
                        val colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    if (lap.isBest) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    },
                            )
                        Card(
                            colors = colors,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (lap.isBest) 2.dp else 1.dp,
                                        color =
                                            if (lap.isBest) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                    ),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Lap ${lap.lapNumber}",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        "${lap.lapTimeS}s",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                                Text(
                                    "Δ best ${lap.deltaToBestS}s ${if (lap.isBest) " · session best" else ""}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("Sectors", style = MaterialTheme.typography.labelLarge)
                                lap.sectors.forEach { s ->
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(s.name, style = MaterialTheme.typography.bodySmall)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "${s.timeS}s",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            if (s.isBest) {
                                                Text(
                                                    " ★",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    style = MaterialTheme.typography.labelMedium,
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
