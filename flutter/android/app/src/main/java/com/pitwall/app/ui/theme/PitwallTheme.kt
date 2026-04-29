package com.pitwall.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Pitwall colour palette ────────────────────────────────────────────────────
object PitwallColors {
    val Background     = Color(0xFF0D0D0D)
    val Surface        = Color(0xFF161616)
    val SurfaceElevated = Color(0xFF1E1E1E)
    val Border         = Color(0xFF2A2A2A)

    val GripGreen      = Color(0xFF00E676)
    val GripGreenDim   = Color(0xFF1B3D29)
    val GripRed        = Color(0xFFFF1744)
    val GripRedDim     = Color(0xFF3D1B1E)
    val GripYellow     = Color(0xFFFFD600)
    val SpeedBlue      = Color(0xFF2979FF)
    val GoldStandard   = Color(0xFFFFAB00)
    val BrakeRed       = Color(0xFFD50000)
    val ThrottleGreen  = Color(0xFF00C853)
    val GForceOrange   = Color(0xFFFF6D00)

    val TextPrimary    = Color(0xFFEEEEEE)
    val TextSecondary  = Color(0xFF9E9E9E)
    val TextDim        = Color(0xFF666666)

    // Semantic aliases used by Compose UI
    val OnTrack  = GripGreen
    val Caution  = GripYellow
    val Danger   = GripRed
}

private val PitwallColorScheme = darkColorScheme(
    primary          = PitwallColors.GripGreen,
    onPrimary        = Color.Black,
    secondary        = PitwallColors.SpeedBlue,
    onSecondary      = Color.White,
    background       = PitwallColors.Background,
    onBackground     = PitwallColors.TextPrimary,
    surface          = PitwallColors.Surface,
    onSurface        = PitwallColors.TextPrimary,
    surfaceVariant   = PitwallColors.SurfaceElevated,
    onSurfaceVariant = PitwallColors.TextSecondary,
    outline          = PitwallColors.Border,
    error            = PitwallColors.GripRed,
)

val MonoFamily = FontFamily.Monospace

@Composable
fun PitwallTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PitwallColorScheme,
        content = content,
    )
}
