package com.pitwall.paddock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = PitwallCyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF0D2A2E),
    onPrimaryContainer = PitwallCyan,
    surface = PitwallSurface,
    onSurface = TextPrimary,
    surfaceContainerHighest = NavBarBg,
    background = PitwallBg,
    onBackground = TextPrimary,
    outline = CardStroke,
    onSurfaceVariant = TextSecondary,
)

@Composable
fun PaddockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
