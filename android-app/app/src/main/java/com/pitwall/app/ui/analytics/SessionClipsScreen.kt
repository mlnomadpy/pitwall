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
import com.pitwall.app.data.remote.NetworkModule
import com.pitwall.app.data.remote.SessionClipsEnvelopeDto
import com.pitwall.app.di.SessionHolder
import retrofit2.HttpException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionClipsScreen(navController: NavController) {
    val sid = SessionHolder.activeSessionId
    var env by remember { mutableStateOf<SessionClipsEnvelopeDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(sid) {
        if (sid.isNullOrBlank()) return@LaunchedEffect
        loading = true
        error = null
        try {
            env = NetworkModule.pitwallApi.sessionClips(sid)
        } catch (e: HttpException) {
            error =
                when (e.code()) {
                    404 -> "No debrief bundle — run Stage clear first."
                    else -> e.message ?: "HTTP ${e.code()}"
                }
            env = null
        } catch (e: Exception) {
            error = e.message ?: e.toString()
            env = null
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session clips") },
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
            env != null -> {
                val e = env!!
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            "${e.count} clips from highlights",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    items(e.clips, key = { it.id }) { c ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                c.title.ifBlank { c.id },
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(12.dp),
                            )
                            Text(
                                "${c.inS}s → ${c.outS}s · lap ${c.lap} · ${c.category} · ${c.severity}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
