package com.pitwall.app.ui.pitstall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pitwall.app.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PitStallScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pit stall") },
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
                "Connect · tune — the web pit stall runs a full boot sequence with bridge health. " +
                    "On Android, jump straight to hardware and live HUD.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NavBtn("Hardware / signal registry", Routes.HARDWARE, navController)
            NavBtn("Live pit wall (overlays)", Routes.PIT_STALL_LIVE, navController)
            NavBtn("On-track HUD (cues stream)", Routes.HUD, navController)
            NavBtn("Bridge sessions", Routes.BRIDGE_SESSIONS, navController)
        }
    }
}

@Composable
private fun NavBtn(
    label: String,
    route: String,
    nav: NavController,
) {
    Button(onClick = { nav.navigate(route) }, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}
