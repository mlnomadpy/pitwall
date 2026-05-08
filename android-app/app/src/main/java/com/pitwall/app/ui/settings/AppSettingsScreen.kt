package com.pitwall.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pitwall.app.BuildConfig
import com.pitwall.app.data.local.AppPreferences
import com.pitwall.app.data.local.SaveStore
import com.pitwall.app.data.remote.NetworkModule
import com.pitwall.app.di.SessionHolder
import com.pitwall.app.ui.navigation.Routes
import com.pitwall.app.ui.theme.NightModeController
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(navController: NavController) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("General", "Driver", "Audio", "Display", "Controls", "Car")

    var driverField by remember { mutableStateOf(AppPreferences.readDriverId(ctx)) }
    var profileEpoch by remember { mutableIntStateOf(0) }
    var bridgeProfile by remember { mutableStateOf<JsonObject?>(null) }
    var bridgeProfileLoading by remember { mutableStateOf(false) }

    LaunchedEffect(profileEpoch) {
        val id = SessionHolder.activeDriver.trim().ifEmpty { return@LaunchedEffect }
        bridgeProfileLoading = true
        bridgeProfile =
            runCatching {
                NetworkModule.pitwallApi.driverProfile(id)
            }.getOrNull()
        bridgeProfileLoading = false
    }

    val slot = SaveStore.activeSlot()
    val s = slot?.settings

    var masterVol by remember(s?.masterVolume) { mutableFloatStateOf((s?.masterVolume ?: 80).toFloat()) }
    var musicVol by remember(s?.musicVolume) { mutableFloatStateOf((s?.musicVolume ?: 50).toFloat()) }
    var sfxVol by remember(s?.sfxVolume) { mutableFloatStateOf((s?.sfxVolume ?: 100).toFloat()) }
    var voiceVol by remember(s?.voiceVolume) { mutableFloatStateOf((s?.voiceVolume ?: 100).toFloat()) }
    var coachMute by remember(s?.coachMute) { mutableStateOf(s?.coachMute ?: false) }

    var nightMode by remember(s?.nightMode) { mutableStateOf(s?.nightMode ?: false) }
    var reducedMotion by remember(s?.reducedMotion) { mutableStateOf(s?.reducedMotion ?: false) }
    var showFps by remember(s?.showFps) { mutableStateOf(s?.showFps ?: false) }

    var layout by remember(s?.keyboardLayout) { mutableStateOf(s?.keyboardLayout ?: "arrows") }
    var swapAb by remember(s?.swapAB) { mutableStateOf(s?.swapAB ?: false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Settings", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Web parity · AUDIO · DISPLAY · CONTROLS · DRIVER · CAR",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    ),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = tab,
                edgePadding = 12.dp,
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text(title, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (tab) {
                    0 -> {
                        Text("Bridge base URL (BuildConfig)", style = MaterialTheme.typography.titleSmall)
                        Text(BuildConfig.PITWALL_API_BASE_URL, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Override via android-app/local.properties → PITWALL_API_BASE_URL.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Active session · ${SessionHolder.activeSessionId ?: "none"}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            "Save slot · ${slot?.driverName ?: "none"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    1 -> {
                        Text(
                            "Driver identity — synced to the active save slot and SessionHolder for coach APIs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = driverField,
                            onValueChange = { driverField = it },
                            label = { Text("Driver name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                AppPreferences.saveDriverId(ctx, driverField)
                                driverField = SessionHolder.activeDriver
                                profileEpoch++
                            },
                        ) {
                            Text("Save driver")
                        }
                        Text(
                            "Active · ${SessionHolder.activeDriver}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { profileEpoch++ }) {
                            Text("Refresh bridge profile")
                        }
                        if (bridgeProfileLoading) {
                            CircularProgressIndicator(
                                modifier =
                                    Modifier.padding(top = 8.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        DriverBridgeProfileCard(bridgeProfile)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            TextButton(onClick = { navController.navigate(Routes.EVOLUTION) }) {
                                Text("Evolution chart")
                            }
                            TextButton(onClick = { navController.navigate(Routes.SESSION_SCORE) }) {
                                Text("Session grade")
                            }
                        }
                    }
                    2 -> {
                        AudioSlider(
                            label = "Master",
                            value = masterVol,
                            onChange = { v ->
                                masterVol = v
                                SaveStore.updateSettings(ctx) { it.copy(masterVolume = v.toInt().coerceIn(0, 100)) }
                            },
                            enabled = slot != null,
                        )
                        AudioSlider(
                            label = "Music",
                            value = musicVol,
                            onChange = { v ->
                                musicVol = v
                                SaveStore.updateSettings(ctx) { it.copy(musicVolume = v.toInt().coerceIn(0, 100)) }
                            },
                            enabled = slot != null,
                        )
                        AudioSlider(
                            label = "SFX",
                            value = sfxVol,
                            onChange = { v ->
                                sfxVol = v
                                SaveStore.updateSettings(ctx) { it.copy(sfxVolume = v.toInt().coerceIn(0, 100)) }
                            },
                            enabled = slot != null,
                        )
                        AudioSlider(
                            label = "Coach voice",
                            value = voiceVol,
                            onChange = { v ->
                                voiceVol = v
                                SaveStore.updateSettings(ctx) { it.copy(voiceVolume = v.toInt().coerceIn(0, 100)) }
                            },
                            enabled = slot != null,
                        )
                        RowToggle(
                            label = "Mute coach",
                            checked = coachMute,
                            onChecked = {
                                coachMute = it
                                SaveStore.updateSettings(ctx) { s -> s.copy(coachMute = it) }
                            },
                            enabled = slot != null,
                        )
                        if (slot == null) {
                            Text("Select a save slot to persist audio levels.", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    3 -> {
                        RowToggle(
                            label = "Night mode (deeper surfaces)",
                            checked = nightMode,
                            onChecked = {
                                nightMode = it
                                SaveStore.updateSettings(ctx) { st -> st.copy(nightMode = it) }
                                NightModeController.applyNightModeEnabled(it)
                            },
                            enabled = slot != null,
                        )
                        RowToggle(
                            label = "Reduced motion",
                            checked = reducedMotion,
                            onChecked = {
                                reducedMotion = it
                                SaveStore.updateSettings(ctx) { st -> st.copy(reducedMotion = it) }
                            },
                            enabled = slot != null,
                        )
                        RowToggle(
                            label = "Show FPS (placeholder)",
                            checked = showFps,
                            onChecked = {
                                showFps = it
                                SaveStore.updateSettings(ctx) { st -> st.copy(showFps = it) }
                            },
                            enabled = slot != null,
                        )
                        Text(
                            "Night mode and motion feed [PitwallTheme] via SaveStore revision.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    4 -> {
                        Text("Keyboard layout", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("arrows" to "Arrows", "wasd" to "WASD", "igdk" to "IJKL").forEach { (id, label) ->
                                FilterChip(
                                    selected = layout == id,
                                    onClick = {
                                        layout = id
                                        SaveStore.updateSettings(ctx) { st -> st.copy(keyboardLayout = id) }
                                    },
                                    label = { Text(label) },
                                    enabled = slot != null,
                                )
                            }
                        }
                        RowToggle(
                            label = "Swap A / B",
                            checked = swapAb,
                            onChecked = {
                                swapAb = it
                                SaveStore.updateSettings(ctx) { st -> st.copy(swapAB = it) }
                            },
                            enabled = slot != null,
                        )
                    }
                    else -> {
                        Text(
                            "Vehicle identity from save slot. Tune springs/aero in Car setup.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            slot?.car ?: "—",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            "Avatar slot · ${slot?.avatarSlot ?: "—"} · ${slot?.driverAvatar ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    enabled: Boolean,
) {
    Text("$label · ${value.toInt()}", style = MaterialTheme.typography.labelMedium)
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = 0f..100f,
        enabled = enabled,
    )
}

@Composable
private fun RowToggle(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    enabled: Boolean,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            enabled = enabled,
        )
    }
}
