package com.pitwall.app.ui.garage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pitwall.app.ui.components.pitwall.PitwallPanelCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarSetupScreen(navController: NavController) {
    val aeroOptions = listOf("MINIMUM", "LOW", "BALANCED", "HIGH", "MAXIMUM")
    val brakeOptions = listOf("REAR ++", "REAR +", "50 / 50", "FRONT +", "FRONT ++")
    val diffOptions = listOf("OPEN", "LOOSE", "BALANCED", "TIGHT", "LOCKED")

    var aeroIdx by remember { mutableIntStateOf(2) }
    var brakeIdx by remember { mutableIntStateOf(2) }
    var diffIdx by remember { mutableIntStateOf(2) }

    val aeroVal = aeroIdx - 2
    val brakeVal = brakeIdx - 2
    val diffVal = diffIdx - 2

    val topSpeedDelta = -(aeroVal * 2)
    val corneringDelta = (aeroVal * 1.5 + diffVal * 0.5).toInt()
    val brakingDelta =
        if (brakeVal == 0) {
            2
        } else {
            -(kotlin.math.abs(brakeVal))
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Car setup", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Aero · brake bias · diff — local tune (Vue parity)",
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PitwallPanelCard(
                title = "Aero",
                subtitle = "Higher aero trades top speed for grip.",
                entranceIndex = 0,
            ) {
                ChipRow(aeroOptions, aeroIdx) { aeroIdx = it }
            }

            PitwallPanelCard(
                title = "Brake bias",
                subtitle = "Front vs rear balance under braking.",
                entranceIndex = 1,
            ) {
                ChipRow(brakeOptions, brakeIdx) { brakeIdx = it }
            }

            PitwallPanelCard(
                title = "Differential",
                subtitle = "Open vs locked affects rotation.",
                entranceIndex = 2,
            ) {
                ChipRow(diffOptions, diffIdx) { diffIdx = it }
            }

            PitwallPanelCard(
                title = "Predicted deltas",
                subtitle = "Illustrative vs baseline — not persisted to bridge.",
                entranceIndex = 3,
            ) {
                Text(
                    "Top speed Δ $topSpeedDelta",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Cornering Δ +$corneringDelta",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Braking Δ ${if (brakingDelta >= 0) "+" else ""}$brakingDelta",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = { navController.navigateUp() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apply & back")
            }
        }
    }
}

@Composable
private fun ChipRow(
    options: List<String>,
    selectedIdx: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEachIndexed { i, opt ->
                FilterChip(
                    selected = selectedIdx == i,
                    onClick = { onSelect(i) },
                    label = { Text(opt, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}
