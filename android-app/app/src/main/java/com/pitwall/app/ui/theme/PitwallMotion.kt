package com.pitwall.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/** Maps PWA `reducedMotion` — disable staggered entrance / pulse when true. */
val LocalPitwallReducedMotion =
    staticCompositionLocalOf { false }
