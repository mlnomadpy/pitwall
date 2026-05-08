package com.pitwall.paddock.ui.theme

import androidx.compose.ui.unit.dp

// ── Design Token Constants ────────────────────────────────────────────────────
// Mirrors the spacing / sizing tokens from global.css

object Dimens {
    // Card corner radii
    val CardCornerSm  = 8.dp
    val CardCornerMd  = 10.dp
    val CardCornerLg  = 12.dp

    // Left accent bar (web Frame component "accent")
    val AccentBarWidth = 4.dp

    // Kerb stripe
    val KerbStripeHeight = 4.dp
    val KerbSegmentWidth = 10.dp   // width of each red/white segment

    // Border strokes
    val BorderNormal = 1.dp
    val BorderThick  = 1.5.dp

    // Spacing — maps to --space-xs/sm/md/lg/xl
    val SpaceXs =  4.dp
    val SpaceSm =  8.dp
    val SpaceMd = 16.dp
    val SpaceLg = 24.dp
    val SpaceXl = 32.dp

    // Glow blurs (used with BlurMaskFilter)
    val GlowRadiusSm = 6f    // px — tight glow
    val GlowRadiusMd = 12f   // px — standard text glow
    val GlowRadiusLg = 20f   // px — wide ambient glow

    // Nav bar
    val NavBarHeight = 64.dp
    val TopBarHeight = 56.dp

    // Status pip
    val PipSize    = 8.dp
    val PipCorner  = 2.dp   // slightly rounded square matching web

    // Touch targets
    val TouchTarget = 48.dp

    // CRT overlay scanline pitch
    val ScanlinePitch = 4.dp   // 2px transparent + 2px dark band
}
