package com.pitwall.app.ui.common

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pitwall.app.data.remote.NetworkModule
import com.pitwall.app.data.remote.PitwallApi
import com.pitwall.app.data.remote.prettyJson
import com.pitwall.app.di.SessionHolder
import kotlinx.serialization.json.JsonObject

/**
 * Loads [fetch] for [SessionHolder.activeSessionId] and shows pretty-printed JSON.
 * Use after POST /coach/debrief when bundle sections exist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionJsonObjectScreen(
    navController: NavController,
    title: String,
    subtitle: String? = null,
    fetch: suspend PitwallApi.(String) -> JsonObject,
) {
    val sid = SessionHolder.activeSessionId
    var text by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(sid, title) {
        if (sid.isNullOrBlank()) {
            text = null
            error = null
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            val json =
                NetworkModule.pitwallApi.run {
                    fetch(sid)
                }
            text = json.prettyJson()
        } catch (e: Exception) {
            error = e.message ?: e.toString()
            text = null
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title)
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
            when {
                sid.isNullOrBlank() ->
                    Text("No active session.", color = MaterialTheme.colorScheme.error)
                loading -> CircularProgressIndicator()
                error != null ->
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                text != null ->
                    Text(text!!, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
