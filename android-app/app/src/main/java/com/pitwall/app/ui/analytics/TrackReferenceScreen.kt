package com.pitwall.app.ui.analytics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pitwall.app.data.local.SaveStore
import com.pitwall.app.data.remote.NetworkModule
import com.pitwall.app.data.remote.prettyJson
import kotlinx.serialization.json.JsonObject

private enum class TrackRefTab(
    val label: String,
) {
    MARKERS("Markers"),
    DANGER("Danger"),
    WEATHER("Weather"),
    ELEVATION("Elevation"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackReferenceScreen(navController: NavController) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = TrackRefTab.entries.toTypedArray()
    val trackId = SaveStore.activeSlot()?.preferredTrack?.trim()?.ifEmpty { null } ?: "sonoma"

    var text by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(tabIndex, trackId) {
        val api = NetworkModule.pitwallApi
        loading = true
        error = null
        text = null
        try {
            val json: JsonObject =
                when (tabs[tabIndex]) {
                    TrackRefTab.MARKERS -> api.trackMarkers()
                    TrackRefTab.DANGER -> api.trackDangerZones()
                    TrackRefTab.WEATHER -> api.trackWeather(hourLocal = null)
                    TrackRefTab.ELEVATION -> api.trackElevation(trackId)
                }
            text = json.prettyJson()
        } catch (e: Exception) {
            error = e.message ?: e.toString()
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Track reference")
                        Text(
                            "GET /track/markers · /danger_zones · /weather · /{id}/elevation",
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
                    .fillMaxSize()
                    .padding(padding),
        ) {
            TabRow(selectedTabIndex = tabIndex) {
                tabs.forEachIndexed { i, t ->
                    Tab(
                        selected = tabIndex == i,
                        onClick = { tabIndex = i },
                        text = { Text(t.label) },
                    )
                }
            }
            Text(
                "Elevation uses save preferred track: $trackId",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                when {
                    loading -> CircularProgressIndicator()
                    error != null ->
                        Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    text != null ->
                        Text(text!!, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
