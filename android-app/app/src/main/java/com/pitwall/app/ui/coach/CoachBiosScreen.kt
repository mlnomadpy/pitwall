package com.pitwall.app.ui.coach

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachBiosScreen(navController: NavController) {
    val bios =
        listOf(
            "T-ROD" to "Sprint-race specialist. Pushes you to find free lap time in traffic and long runs.",
            "BUDDY" to "Encouraging co-pilot. Great when you are learning a new track or car balance.",
            "DRILL SGT" to "High intensity feedback. Unlocks when you are ready for brutal honesty.",
            "BENTLEY" to "Data-first pedagogy. Ties numbers to what you feel in the seat.",
            "CALM PRO" to "Narrative, breath, and vision work for high-stakes sessions.",
        )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coach bios") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Text(
            text =
                bios.joinToString("\n\n") { (name, text) ->
                    "— $name —\n$text"
                },
            style = MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        )
    }
}
