package com.pitwall.app.ui.garage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pitwall.app.data.local.SaveStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainerCardScreen(navController: NavController) {
    val save = SaveStore.activeSlot()
    var tab by remember { mutableIntStateOf(0) }
    val skills = listOf("BRAKING" to 85, "CONSISTENCY" to 72, "APEX" to 90, "SPEED" to 88, "VISION" to 65)
    val bestLapS =
        save?.bestLapBySession?.values?.minOrNull()
            ?: save?.sessions?.mapNotNull { it.bestLapS }?.minOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trainer card") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (save == null) {
                Text("No active save slot.", color = MaterialTheme.colorScheme.error)
            } else {
                Text(
                    save.driverName.uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    "${save.car} · ${save.preferredTrack.uppercase()} · Lv ${save.level}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Best lap: ${formatLap(bestLapS)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        label = { Text("Skills") },
                    )
                    FilterChip(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        label = { Text("Medals") },
                    )
                }
                when (tab) {
                    0 -> {
                        skills.forEach { (k, v) ->
                            Text(
                                "$k — $v / 100",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            "Radar / DuckDB aggregates are PWA + in-browser; shown values mirror the Vue trainer mock.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        Text(
                            if (save.medals.isEmpty()) {
                                "No medals yet — hit session goals in the web client or extend native save sync."
                            } else {
                                save.medals.joinToString()
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

private fun formatLap(seconds: Double?): String {
    if (seconds == null) return "--:--.-"
    val min = (seconds / 60).toInt()
    val sec = seconds % 60
    return "%d:%05.2f".format(min, sec)
}
