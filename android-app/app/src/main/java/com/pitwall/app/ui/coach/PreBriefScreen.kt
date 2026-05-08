package com.pitwall.app.ui.coach

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pitwall.app.data.remote.BriefResponseDto
import com.pitwall.app.data.remote.NetworkModule
import com.pitwall.app.di.SessionHolder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreBriefScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var brief by remember { mutableStateOf<BriefResponseDto?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pre-brief") },
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
                "Warm-path coach brief (requires LiteRT / bridge). Session: ${SessionHolder.activeSessionId ?: "none"}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        brief =
                            try {
                                NetworkModule.pitwallApi.coachBrief(
                                    driver = SessionHolder.activeDriver,
                                    sessionId = SessionHolder.activeSessionId,
                                )
                            } catch (e: Exception) {
                                error = e.message ?: e.toString()
                                null
                            }
                        loading = false
                    }
                },
                enabled = !loading,
            ) {
                Text(if (loading) "Loading…" else "Fetch brief")
            }
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
                error != null ->
                    Text(
                        error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                brief != null -> {
                    val b = brief!!
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = b.narrativeMd,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        Text(
                            "Focus: ${b.focus.joinToString()}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}
