package com.pitwall.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pitwall.app.data.local.AppPreferences
import com.pitwall.app.data.local.SaveStore
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pitwall.app.ui.navigation.PitwallNavHost
import com.pitwall.app.ui.theme.NightModeController
import com.pitwall.app.ui.theme.PitwallTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPreferences.hydrateSessionHolder(applicationContext)
        SaveStore.hydrate(applicationContext)
        NightModeController.applyNightModeEnabled(
            SaveStore.activeSlot()?.settings?.nightMode == true,
        )
        enableEdgeToEdge()
        setContent {
            PitwallTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PitwallNavHost()
                }
            }
        }
    }
}
