package com.pitwall.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.pitwall.app.R

/** Matches `src/pwa/src/app/styles/global.css` — Orbitron / Rajdhani / Share Tech Mono. */
val PitwallFontTitle =
    FontFamily(
        Font(R.font.orbitron_family, FontWeight.Bold),
        Font(R.font.orbitron_family, FontWeight.Normal),
    )

val PitwallFontUi =
    FontFamily(
        Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
        Font(R.font.rajdhani_regular, FontWeight.Medium),
        Font(R.font.rajdhani_regular, FontWeight.Normal),
    )

val PitwallFontMono = FontFamily(Font(R.font.share_tech_mono, FontWeight.Normal))
