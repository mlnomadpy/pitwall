package com.pitwall.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitwall.app.data.local.SaveStore
import com.pitwall.app.entities.save.SaveSettingsSlot

@Composable
fun PitwallTheme(content: @Composable () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val rev by SaveStore.uiRevision.collectAsStateWithLifecycle(
        initialValue = 0L,
        lifecycle = lifecycleOwner.lifecycle,
    )
    val settings =
        remember(rev) {
            SaveStore.activeSlot()?.settings ?: SaveSettingsSlot()
        }
    val colorScheme =
        remember(settings.nightMode) {
            pitwallDarkColorScheme(deepSurfaces = settings.nightMode)
        }

    CompositionLocalProvider(
        LocalPitwallReducedMotion provides settings.reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PitwallTypography,
            content = content,
        )
    }
}
