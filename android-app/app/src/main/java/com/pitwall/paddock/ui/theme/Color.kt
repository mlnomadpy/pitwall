package com.pitwall.paddock.ui.theme

import androidx.compose.ui.graphics.Color

// ── Core Palette: Deep Space / Synthwave ──────────────────────────────────────
// Exact token parity with src/pwa/src/app/styles/global.css

val ColorInk        = Color(0xFF0B0C10)   // --color-ink        (background)
val ColorCharcoal   = Color(0xFF1F2833)   // --color-charcoal   (surface)
val ColorSlate      = Color(0xFF66C2BE)   // --color-slate      (muted text / borders)
val ColorSilver     = Color(0xFFC5C6C7)   // --color-silver     (body text)

// ── Cyber-Retro Neons ────────────────────────────────────────────────────────
val ColorUiGood     = Color(0xFF4ECDC4)   // --color-ui-good    (cyber cyan, primary accent)
val ColorUiWarn     = Color(0xFFFECA57)   // --color-ui-warn    (arcade yellow)
val ColorUiBad      = Color(0xFFFF4757)   // --color-ui-bad     (neon red)
val ColorUiInfo     = Color(0xFF45B7D1)   // --color-ui-info    (electric blue)
val ColorUiQuest    = Color(0xFFC44569)   // --color-ui-quest   (arcade magenta)

// ── Extended Semantic ────────────────────────────────────────────────────────
val ColorBiosGreen  = Color(0xFF4ADE80)   // --color-bios-green (progress / positive)
val ColorNeonGreen  = Color(0xFF5EED71)   // --color-neon-green
val ColorDeltaRed   = Color(0xFFEF4444)   // --color-delta-red
val ColorAmber      = Color(0xFFF59E0B)   // --color-amber
val ColorGoldDark   = Color(0xFFEAB308)   // --color-gold-dark
val ColorPurpleGlow = Color(0xFFC084FC)   // --color-purple-glow
val ColorCharcoalLight = Color(0xFF4A5568)
val ColorCharcoalMid   = Color(0xFF3A4550)
val ColorPanelBgAlt    = Color(0xFF1A252C)

// ── Kerb / Track surface ─────────────────────────────────────────────────────
val ColorCurbRed    = Color(0xFFC93838)   // --color-curb-red
val ColorCurbWhite  = Color(0xFFF5F5E8)   // --color-curb-white

// ── Rank badges ──────────────────────────────────────────────────────────────
val ColorRankGold   = Color(0xFFFBBF24)   // --color-rank-gold
val ColorRankSilver = Color(0xFF94A3B8)   // --color-rank-silver
val ColorRankBronze = Color(0xFFB45309)   // --color-rank-bronze

// ── Skin tones (coach sprites) ────────────────────────────────────────────────
val ColorSkinShadow = Color(0xFFB07658)
val ColorSkinMid    = Color(0xFFD89878)
val ColorSkinLight  = Color(0xFFECB898)

// ── Asphalt ──────────────────────────────────────────────────────────────────
val ColorAsphaltDeep  = Color(0xFF0B0C10)
val ColorAsphaltMid   = Color(0xFF1F2833)
val ColorAsphaltLight = Color(0xFF2C3E50)

// ── Semantic aliases (kept for compatibility) ─────────────────────────────────
@Deprecated("Use ColorInk", ReplaceWith("ColorInk"))
val PitwallBg      = ColorInk
@Deprecated("Use ColorCharcoal", ReplaceWith("ColorCharcoal"))
val PitwallSurface = ColorCharcoal
@Deprecated("Use ColorUiGood", ReplaceWith("ColorUiGood"))
val PitwallCyan    = ColorUiGood
@Deprecated("Use ColorUiGood", ReplaceWith("ColorUiGood"))
val PitwallCyanDim = ColorUiGood.copy(alpha = 0.7f)
@Deprecated("Use ColorSilver", ReplaceWith("ColorSilver"))
val TextPrimary    = ColorSilver
@Deprecated("Use ColorSlate", ReplaceWith("ColorSlate"))
val TextSecondary  = ColorSlate.copy(alpha = 0.7f)
@Deprecated("Use ColorSlate", ReplaceWith("ColorSlate"))
val TextMuted      = ColorSlate.copy(alpha = 0.4f)
val NavBarBg       = ColorInk
val CardStroke     = ColorCharcoal
val AccentGreen    = ColorBiosGreen
val AccentPeach    = ColorUiWarn
val AccentPink     = ColorUiBad
val AccentCyanBar  = ColorUiGood
val AccentRed      = ColorUiBad
