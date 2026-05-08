package com.pitwall.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/** Palette aligned with PWA cyber tokens (ink, slate, curb, ui-good / warn). */
object PitwallPalette {
    val Ink = Color(0xFF0D0D12)
    val InkDeep = Color(0xFF060608)
    val Surface = Color(0xFF14141C)
    val SurfaceElevated = Color(0xFF1C1C28)
    val Slate = Color(0xFF6B7280)
    val Silver = Color(0xFFC9D1D9)
    val CyanPrimary = Color(0xFF2AA198)
    val OnCyan = Color(0xFF0A0A0F)
    val CurbRed = Color(0xFFE74C3C)
    val UiGood = Color(0xFF5FD68A)
    val UiWarn = Color(0xFFF4D03F)
    val OutlineSubtle = Color(0xFF2A2A38)
}

fun pitwallDarkColorScheme(deepSurfaces: Boolean = true) =
    darkColorScheme(
        primary = PitwallPalette.CyanPrimary,
        onPrimary = PitwallPalette.OnCyan,
        primaryContainer = PitwallPalette.SurfaceElevated,
        onPrimaryContainer = PitwallPalette.Silver,
        secondary = PitwallPalette.CurbRed,
        onSecondary = Color.White,
        tertiary = PitwallPalette.UiWarn,
        onTertiary = PitwallPalette.Ink,
        background = if (deepSurfaces) PitwallPalette.InkDeep else PitwallPalette.Ink,
        onBackground = PitwallPalette.Silver,
        surface = if (deepSurfaces) PitwallPalette.Surface else PitwallPalette.SurfaceElevated,
        onSurface = PitwallPalette.Silver,
        surfaceVariant = PitwallPalette.SurfaceElevated,
        onSurfaceVariant = PitwallPalette.Slate,
        outline = PitwallPalette.OutlineSubtle,
        outlineVariant = PitwallPalette.OutlineSubtle.copy(alpha = 0.5f),
        error = PitwallPalette.CurbRed,
        onError = Color.White,
    )
