package com.pitwall.paddock.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.R

// ── Font Families ─────────────────────────────────────────────────────────────
// Exact parity with web:
//   --font-title: 'Orbitron'   → titles, headings, numerics on track
//   --font-ui:    'Rajdhani'   → body copy, labels, nav items
//   --font-nums:  'Share Tech Mono' → telemetry numbers, lap times, deltas

/** Orbitron — variable font (wght 400–900). Loaded as a variable font family. */
@androidx.compose.runtime.Stable
val OrbitronFamily = FontFamily(
    Font(R.font.orbitron_variable, weight = FontWeight.Normal),
    Font(R.font.orbitron_variable, weight = FontWeight.SemiBold),
    Font(R.font.orbitron_variable, weight = FontWeight.Bold),
    Font(R.font.orbitron_variable, weight = FontWeight.Black),
)

/** Rajdhani — body text, labels, tab titles. */
val RajdhaniFamily = FontFamily(
    Font(R.font.rajdhani_regular,  FontWeight.Normal),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold,     FontWeight.Bold),
)

/** Share Tech Mono — telemetry numbers, lap times, speed readouts. */
val ShareTechMonoFamily = FontFamily(
    Font(R.font.share_tech_mono_regular, FontWeight.Normal),
)

// ── Material3 Typography wiring ───────────────────────────────────────────────
// Maps web text utility classes to M3 roles:
//   .text-title-lg  → displayLarge  (Orbitron, 40sp)
//   .text-title     → displayMedium (Orbitron, 32sp)
//   .text-title-sm  → displaySmall  (Orbitron, 24sp)
//   .text-body      → bodyLarge     (Rajdhani, 16sp)
//   .text-small     → bodySmall     (Rajdhani, 13sp)
//   .text-num-lg    → headlineLarge (ShareTechMono, 32sp)
//   .text-num       → headlineMedium(ShareTechMono, 24sp)

val PitwallTypography = Typography(
    // ── Display / Title (Orbitron) ───────────────────────────────────────────
    displayLarge = TextStyle(
        fontFamily = OrbitronFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.05.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = OrbitronFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.05.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = OrbitronFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.08.sp,
    ),
    // ── Headlines (Share Tech Mono — numeric readouts) ───────────────────────
    headlineLarge = TextStyle(
        fontFamily = ShareTechMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = ShareTechMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = ShareTechMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    // ── Titles (Orbitron — section headers) ──────────────────────────────────
    titleLarge = TextStyle(
        fontFamily = OrbitronFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = OrbitronFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = OrbitronFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp,
    ),
    // ── Body (Rajdhani — all prose text) ─────────────────────────────────────
    bodyLarge = TextStyle(
        fontFamily = RajdhaniFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = RajdhaniFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = RajdhaniFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    // ── Labels (Rajdhani SemiBold — nav, chips, captions) ────────────────────
    labelLarge = TextStyle(
        fontFamily = RajdhaniFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = RajdhaniFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = RajdhaniFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 1.5.sp,
    ),
)
