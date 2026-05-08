package com.pitwall.app.ui.save

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pitwall.app.data.local.SaveStore
import com.pitwall.app.ui.components.pitwall.PitwallSectionHeader
import com.pitwall.app.ui.components.pitwall.PitwallSlotCard
import com.pitwall.app.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveSelectScreen(navController: NavController) {
    val ctx = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SELECT SAVE SLOT", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Three slots · same contract as the web client",
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
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text =
                    "Pick a slot. Empty slots begin onboarding; occupied slots return you to the garage with your driver profile.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )

            PitwallSectionHeader("Slots")

            for (id in 1..3) {
                val slot = SaveStore.slot(id)
                PitwallSlotCard(
                    slotId = id,
                    slot = slot,
                    onClick = {
                        SaveStore.setActiveSlot(ctx, id)
                        if (slot != null) {
                            SaveStore.touchSlot(ctx, id)
                            navController.navigate(Routes.GARAGE) {
                                launchSingleTop = true
                            }
                        } else {
                            navController.navigate("onboarding/1") {
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }

            Spacer(Modifier.height(8.dp))

            PitwallSectionHeader("Live data")

            OutlinedButton(
                onClick = {
                    navController.navigate(Routes.BRIDGE_SESSIONS) {
                        launchSingleTop = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                Text("Bridge sessions · Flask /sessions")
            }

            Text(
                "Bridge sessions set [SessionHolder] when you open a row — use before HUD or bundle analysis.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Button(
                onClick = { navController.navigate(Routes.GARAGE) },
                enabled = SaveStore.activeSlot() != null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
            ) {
                Text("Continue to garage")
            }
        }
    }
}
