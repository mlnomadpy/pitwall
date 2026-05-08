package com.pitwall.app.ui.quests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pitwall.app.data.local.SaveStore
import com.pitwall.app.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestLogScreen(navController: NavController) {
    val save = SaveStore.activeSlot()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quest log") },
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
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Session goals and contract progress are stored in the PWA save format. " +
                    "When this slot gains goals in the web client, sync is future work; " +
                    "for now the native app focuses on live bridge sessions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (save == null) {
                Text("No active save.", color = MaterialTheme.colorScheme.error)
            } else if (save.goalsHistory.isEmpty()) {
                Text("0 active goals in this save slot (same as a fresh PWA profile).", style = MaterialTheme.typography.bodyMedium)
            } else {
                save.goalsHistory.forEach { g ->
                    Text("· $g", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                "Sponsor meta-game →",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = { navController.navigate(Routes.SPONSORS) }) {
                Text("Sponsor contracts")
            }
        }
    }
}
