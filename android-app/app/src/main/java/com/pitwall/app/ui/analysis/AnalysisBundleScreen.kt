package com.pitwall.app.ui.analysis

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pitwall.app.data.remote.NetworkModule
import com.pitwall.app.data.remote.PitwallApi
import com.pitwall.app.data.remote.compactSummary
import com.pitwall.app.data.remote.prettyJson
import com.pitwall.app.data.remote.topLevelNumericFractions
import com.pitwall.app.di.SessionHolder
import com.pitwall.app.ui.components.pitwall.PitwallHorizontalBar
import kotlinx.serialization.json.JsonObject
import retrofit2.HttpException

private enum class BundleSection(
    val label: String,
) {
    HIGHLIGHTS("Highlights"),
    STATS("Stats"),
    FRICTION("Friction"),
    HUSTLE("Hustle"),
    EOB("EOB"),
    INCIDENTS("Incidents"),
}

private suspend fun PitwallApi.loadBundleSection(
    sid: String,
    section: BundleSection,
): JsonObject =
    when (section) {
        BundleSection.HIGHLIGHTS -> sessionHighlights(sid)
        BundleSection.STATS -> sessionStats(sid)
        BundleSection.FRICTION -> sessionFrictionCircle(sid)
        BundleSection.HUSTLE -> sessionHustleMap(sid)
        BundleSection.EOB -> sessionEob(sid)
        BundleSection.INCIDENTS -> sessionIncidents(sid)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisBundleScreen(navController: NavController) {
    var section by remember { mutableStateOf(BundleSection.HIGHLIGHTS) }
    val sid = SessionHolder.activeSessionId
    var text by remember { mutableStateOf<String?>(null) }
    var bundleJson by remember { mutableStateOf<JsonObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(sid, section) {
        if (sid.isNullOrBlank()) {
            text = null
            bundleJson = null
            error = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            val api = NetworkModule.pitwallApi
            val json = api.loadBundleSection(sid, section)
            bundleJson = json
            text =
                if (section == BundleSection.STATS) {
                    json.compactSummary()
                } else {
                    json.prettyJson()
                }
        } catch (e: HttpException) {
            error =
                when (e.code()) {
                    404 ->
                        "No analysis bundle — run Post-session debrief (Stage clear) for this session first."
                    else -> e.message ?: "HTTP ${e.code()}"
                }
            text = null
            bundleJson = null
        } catch (e: Exception) {
            error = e.message ?: e.toString()
            text = null
            bundleJson = null
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Debrief bundle")
                        Text(
                            "GET /session/…/{highlights|stats|…}",
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
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BundleSection.values().forEach { s ->
                    FilterChip(
                        selected = section == s,
                        onClick = { section = s },
                        label = { Text(s.label) },
                    )
                }
            }
            Text(
                "Session: ${sid ?: "none"}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
            ) {
                when {
                    sid.isNullOrBlank() ->
                        Text("Select a session under Sessions first.", color = MaterialTheme.colorScheme.error)
                    loading -> CircularProgressIndicator()
                    error != null ->
                        Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    text != null -> {
                        Column {
                            if (section == BundleSection.STATS && bundleJson != null) {
                                val bars = bundleJson!!.topLevelNumericFractions().take(14)
                                if (bars.isNotEmpty()) {
                                    Text(
                                        "Numeric snapshot (normalized to largest magnitude)",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    )
                                    bars.forEach { (k, frac) ->
                                        PitwallHorizontalBar(
                                            label = k,
                                            fraction = frac,
                                            caption = "${(frac * 100).toInt()}%",
                                        )
                                    }
                                }
                            }
                            Text(text!!, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
