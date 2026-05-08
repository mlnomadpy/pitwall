package com.pitwall.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/** Palette aligned with `src/pwa/src/app/styles/global.css` (`--color-ink`, `--color-ui-good`, …). */
object PitwallPalette {
    val Ink = Color(0xFF0b0c10)
    val InkDeep = Color(0xFF050505)
    val Charcoal = Color(0xFF1f2833)
    val Surface = Color(0xFF1A252C)
    val SurfaceElevated = Color(0xFF242830)

    /** Tailwind `slate` in the PWA theme — teal accent, not gray. */
    val AccentSlate = Color(0xFF66c2be)
    val Silver = Color(0xFFc5c6c7)
    /** Secondary / muted body text (between silver and ink). */
    val SlateMuted = Color(0xFF6B7280)

    val UiGood = Color(0xFF4ecdc4)
    val OnUiGood = Ink
    val UiWarn = Color(0xFFfeca57)
    val CurbRed = Color(0xFFc93838)
    val CurbWhite = Color(0xFFf5f5e8)
    val CyanPrimary = AccentSlate
    val OutlineSubtle = Color(0xFF2A3644)

    /** Sprite / warm glow accents used on the title (see `TitleScreen.vue`). */
    val TitleGlowWarm = Color(0xFFC8786A)
}

fun pitwallDarkColorScheme(deepSurfaces: Boolean = true) =
    darkColorScheme(
        primary = PitwallPalette.UiGood,
        onPrimary = PitwallPalette.OnUiGood,
        primaryContainer = PitwallPalette.Charcoal,
        onPrimaryContainer = PitwallPalette.Silver,
        secondary = PitwallPalette.CurbRed,
        onSecondary = Color.White,
        tertiary = PitwallPalette.UiWarn,
        onTertiary = PitwallPalette.Ink,
        background = if (deepSurfaces) PitwallPalette.InkDeep else PitwallPalette.Ink,
        onBackground = PitwallPalette.Silver,
        surface = if (deepSurfaces) PitwallPalette.Charcoal else PitwallPalette.Surface,
        onSurface = PitwallPalette.Silver,
        surfaceVariant = PitwallPalette.SurfaceElevated,
        onSurfaceVariant = PitwallPalette.SlateMuted,
        outline = PitwallPalette.AccentSlate.copy(alpha = 0.45f),
        outlineVariant = PitwallPalette.OutlineSubtle,
        error = PitwallPalette.CurbRed,
        onError = Color.White,
    )
