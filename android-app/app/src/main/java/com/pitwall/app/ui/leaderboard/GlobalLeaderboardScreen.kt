package com.pitwall.app.ui.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

private data class LbEntry(
    val rank: Int,
    val initials: String,
    val car: String,
    val track: String,
    val time: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalLeaderboardScreen(navController: NavController) {
    var entries by remember { mutableStateOf<List<LbEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var cursor by remember { mutableIntStateOf(3) }

    LaunchedEffect(Unit) {
        delay(800)
        entries =
            listOf(
                LbEntry(1, "TRD", "GT3_911", "SONOMA", "1:34.210"),
                LbEntry(2, "BTY", "M4_GT3", "SONOMA", "1:35.050"),
                LbEntry(3, "DRL", "AMG_GT3", "SONOMA", "1:35.800"),
                LbEntry(4, "YOU", "GT3_911", "SONOMA", "1:36.450"),
                LbEntry(5, "CLM", "720S_GT3", "SONOMA", "1:37.110"),
                LbEntry(6, "BDY", "M4_GT3", "SONOMA", "1:38.000"),
                LbEntry(7, "AI1", "GT3_911", "SONOMA", "1:38.500"),
                LbEntry(8, "AI2", "AMG_GT3", "SONOMA", "1:39.100"),
            )
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("High scores") },
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
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text(
                "Same mock ladder as the PWA leaderboard store (no Flask endpoint yet).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            if (loading) {
                Text("Loading…", modifier = Modifier.align(Alignment.CenterHorizontally))
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
                    itemsIndexed(entries) { i, e ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (i == cursor) "▶ ${e.rank}" else "${e.rank}",
                                fontFamily = FontFamily.Monospace,
                                color =
                                    if (i == cursor) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                            Text(e.initials, fontFamily = FontFamily.Monospace)
                            Text(e.car, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            Text(e.time, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
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
