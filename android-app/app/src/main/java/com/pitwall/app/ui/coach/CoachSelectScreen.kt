package com.pitwall.app.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pitwall.app.data.local.SaveStore
import com.pitwall.app.ui.components.pitwall.PitwallTileCard
import com.pitwall.app.ui.navigation.Routes

private data class CoachRow(
    val id: String,
    val name: String,
    val levelReq: Int,
    val quote: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachSelectScreen(navController: NavController) {
    val ctx = LocalContext.current
    val coaches =
        remember {
            listOf(
                CoachRow("trod", "T-ROD", 1, "Distance is king. Smooth is fast."),
                CoachRow("buddy", "BUDDY", 1, "Let's keep the car out of the wall today."),
                CoachRow("drill", "DRILL SGT", 5, "YOU ARE BRAKING TOO EARLY!"),
                CoachRow("bentley", "BENTLEY", 10, "The slip angle vector is sub-optimal."),
                CoachRow("calm", "CALM PRO", 15, "Breathe. Feel the grip."),
            )
        }
    val slot = SaveStore.activeSlot()
    val preferredId = slot?.preferredCoach

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Coach select", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Tap a coach · affinity saves to your slot",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            coaches.forEachIndexed { i, c ->
                val locked = slot != null && slot.level < c.levelReq
                val subtitle =
                    when {
                        locked -> "Locked · requires level ${c.levelReq}"
                        else -> "${c.quote} · Tap to set preferred"
                    }
                PitwallTileCard(
                    title = c.name,
                    subtitle = subtitle,
                    onClick = {
                        if (!locked && slot != null) {
                            SaveStore.updatePreferredCoach(ctx, c.id)
                        }
                    },
                    enabled = slot != null && !locked,
                    emphasized = !locked && preferredId == c.id,
                    entranceIndex = i,
                )
            }

            if (slot == null) {
                Text(
                    "Select a save slot first — coach affinity is stored per driver.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            FilledTonalButton(
                onClick = { navController.navigate(Routes.COACH_BIOS) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
            ) {
                Text("Coach bios")
            }

            Button(
                onClick = { navController.navigate(Routes.BRIEFING) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Pre-brief with preferred coach")
            }
        }
    }
}
