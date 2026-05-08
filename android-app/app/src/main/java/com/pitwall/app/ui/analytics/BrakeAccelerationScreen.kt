package com.pitwall.app.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pitwall.app.data.remote.BrakeAccelerationDto
import com.pitwall.app.data.remote.NetworkModule
import com.pitwall.app.di.SessionHolder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrakeAccelerationScreen(navController: NavController) {
    val sid = SessionHolder.activeSessionId
    var data by remember { mutableStateOf<BrakeAccelerationDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(sid) {
        if (sid.isNullOrBlank()) return@LaunchedEffect
        loading = true
        error = null
        try {
            data = NetworkModule.pitwallApi.brakeAcceleration(sid)
        } catch (e: Exception) {
            error = e.message ?: e.toString()
            data = null
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Brake & exit accel") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            sid.isNullOrBlank() ->
                Text(
                    "No active session.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(padding).padding(16.dp),
                )
            loading ->
                CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
            error != null ->
                Text(
                    error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(padding).padding(16.dp),
                )
            data != null -> {
                val d = data!!
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text("Heavy braking", style = MaterialTheme.typography.titleSmall)
                    }
                    items(d.brakeZones, key = { it.corner }) { z ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "${z.corner}: peak ${z.maxDecelG}g · ${z.durationS}s · ${z.nPasses} passes",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    item {
                        Text("Corner exits", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(d.cornerExits, key = { it.corner }) { x ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "${x.corner}: ${x.maxLongAccelG}g long · ${x.exitSpeedKmh} km/h exit",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
