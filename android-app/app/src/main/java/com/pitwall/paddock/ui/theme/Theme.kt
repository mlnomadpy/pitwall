package com.pitwall.paddock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Material3 Color Scheme — web token parity ─────────────────────────────────
private val DarkColors = darkColorScheme(
    // Primary: cyber cyan (#4ECDC4)
    primary             = ColorUiGood,
    onPrimary           = Color(0xFF002B29),
    primaryContainer    = Color(0xFF003D3A),
    onPrimaryContainer  = ColorUiGood,
    // Secondary: electric blue
    secondary           = ColorUiInfo,
    onSecondary         = Color.Black,
    secondaryContainer  = Color(0xFF002030),
    onSecondaryContainer = ColorUiInfo,
    // Tertiary: arcade yellow
    tertiary            = ColorUiWarn,
    onTertiary          = Color(0xFF2A1F00),
    // Error: neon red
    error               = ColorUiBad,
    onError             = Color.Black,
    // Backgrounds
    background          = ColorInk,
    onBackground        = ColorSilver,
    surface             = ColorCharcoal,
    onSurface           = ColorSilver,
    surfaceVariant      = ColorCharcoalMid,
    onSurfaceVariant    = ColorSlate,
    surfaceContainerHighest = ColorInk,   // nav bar
    // Borders / outlines
    outline             = ColorSlate.copy(alpha = 0.3f),
    outlineVariant      = ColorCharcoal,
    // Inverse
    inverseSurface      = ColorSilver,
    inverseOnSurface    = ColorInk,
)

@Composable
fun PaddockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography  = PitwallTypography,
        content     = content,
    )
}

