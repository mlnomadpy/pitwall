package com.pitwall.app.ui.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitwall.app.BuildConfig
import com.pitwall.app.data.local.SaveStore
import com.pitwall.app.ui.theme.PitwallPalette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * Top overlay aligned with PWA [BridgeOfflineBanner.vue]:
 * after **3** consecutive failed `/health` polls, show a reconnecting strip; on recovery, flash success.
 * Tap opens diagnostics (URL, last error, retry count).
 */
@Composable
fun BridgeOfflineBanner(
    viewModel: BridgeStatusViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val failures by viewModel.consecutiveFailures.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val fetchedAt by viewModel.healthFetchedAt.collectAsStateWithLifecycle()

    var expanded by remember { mutableStateOf(false) }
    var showReconnected by remember { mutableStateOf(false) }

    var prevFailures by remember { mutableIntStateOf(failures) }

    LaunchedEffect(failures) {
        // Match PWA: only celebrate recovery after we had enough failures to show the offline strip (≥3).
        if (failures == 0 && prevFailures >= 3) {
            showReconnected = true
            expanded = false
            delay(1500)
            showReconnected = false
        }
        prevFailures = failures
    }

    /** Hide offline strip as soon as [failures] drops below 3; green flash is driven separately. */
    val visibleOffline = failures >= 3 && !showReconnected

    if (expanded) {
        DiagnosticsDialog(
            baseUrl = BuildConfig.PITWALL_API_BASE_URL,
            errorText = error,
            retries = failures,
            fetchedAt = fetchedAt,
            onDismiss = { expanded = false },
            onRetry = { viewModel.refreshHealth() },
            onCopy = { payload ->
                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clip.setPrimaryClip(ClipData.newPlainText("pitwall-bridge-diag", payload))
            },
        )
    }

    if (!visibleOffline && !showReconnected) {
        return
    }

    Column(modifier.fillMaxWidth()) {
        when {
            showReconnected ->
                Surface(
                    color = PitwallPalette.UiGood,
                    tonalElevation = 3.dp,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding(),
                ) {
                    Text(
                        text = "BRIDGE RECONNECTED",
                        style = MaterialTheme.typography.labelLarge,
                        color = PitwallPalette.InkDeep,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                    )
                }
            visibleOffline ->
                Surface(
                    color = MaterialTheme.colorScheme.error,
                    tonalElevation = 4.dp,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .clickable {
                                expanded = true
                            },
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "BRIDGE OFFLINE — RECONNECTING…",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onError,
                        )
                        Text(
                            text = "tap for details",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onError.copy(alpha = 0.85f),
                        )
                    }
                }
        }
    }
}

@Composable
private fun DiagnosticsDialog(
    baseUrl: String,
    errorText: String?,
    retries: Int,
    fetchedAt: Long,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val coach = SaveStore.activeSlot()?.preferredCoach?.uppercase() ?: "—"
    val fmt =
        remember {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
        }
    val lastOk =
        if (fetchedAt > 0L) {
            fmt.format(Instant.ofEpochMilli(fetchedAt))
        } else {
            "never"
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bridge diagnostic") },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            coach,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "\"Lost the bridge. Let's get you back online.\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LabeledRow("URL", baseUrl)
                    LabeledRow("LAST OK", lastOk)
                    LabeledRow("ERROR", errorText ?: "unknown")
                    LabeledRow("RETRIES", "$retries (poll every 5 s)")
                }
                Text(
                    "Troubleshooting:\n" +
                        "• Start the Flask bridge (see repo README).\n" +
                        "• Emulator → host: use http://10.0.2.2:8765/ in local.properties.\n" +
                        "• adb reverse tcp:8765 tcp:8765 when tunneling to a device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        val payload =
                            """
                            {
                              "url": "$baseUrl",
                              "error": "${errorText ?: ""}",
                              "retries": $retries
                            }
                            """.trimIndent()
                        onCopy(payload)
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text("Copy JSON")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text("Retry now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun LabeledRow(
    label: String,
    value: String,
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
