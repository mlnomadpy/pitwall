package com.pitwall.app.ui.analytics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pitwall.app.data.remote.prettyJson
import com.pitwall.app.di.SessionHolder
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import com.pitwall.app.ui.components.pitwall.LapTimeLineChart
import com.pitwall.app.ui.components.pitwall.MiniSparkline
import com.pitwall.app.ui.settings.DriverBridgeProfileCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverEvolutionScreen(
    navController: NavController,
    vm: DriverEvolutionViewModel = viewModel(),
) {
    val driver = SessionHolder.activeDriver
    val lapTrend by vm.lapTrend.collectAsStateWithLifecycle()
    val pbSparkline by vm.pbSparkline.collectAsStateWithLifecycle()
    val profileJson by vm.profileJson.collectAsStateWithLifecycle()
    val evolutionJson by vm.evolutionJson.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()

    LaunchedEffect(driver) {
        vm.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Driver evolution")
                        Text(
                            "GET /driver/{id}/evolution · GET /driver/{id}/profile",
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
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(16.dp),
        ) {
            Text(
                "Driver · $driver",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            profileJson?.let { DriverBridgeProfileCard(it) }
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp))
                error != null ->
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
            }
            if (lapTrend.size >= 2) {
                Text(
                    "Best lap trend (session evolution)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                LapTimeLineChart(lapTimesSeconds = lapTrend)
                Text(
                    "Lower line toward the top = faster laps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (pbSparkline.size >= 2) {
                Text(
                    "Personal-best lap history (profile events)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                MiniSparkline(values = pbSparkline)
            }
            evolutionJson?.let { json ->
                json["note"]?.jsonPrimitive?.contentOrNull?.let { note ->
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                Text(
                    json.prettyJson(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}
