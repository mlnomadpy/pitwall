package com.pitwall.paddock.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    baseUrl: String,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        Text(
            "Pitwall API (BuildConfig PITWALL_API_BASE_URL)",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(baseUrl, style = MaterialTheme.typography.bodySmall)
        Text(
            "Override in android-app/local.properties as PITWALL_API_BASE_URL. Emulator default targets host via 10.0.2.2:8765.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Set MAPS_API_KEY for Google Maps. Keys are restricted in Cloud Console to Android package com.pitwall.paddock and SHA-1 of your debug keystore.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
