package com.pitwall.paddock.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Full-screen CRT scanline overlay.
 *
 * Matches web .crt-overlay:
 *   repeating-linear-gradient(0deg, transparent 2px, rgba(0,0,0,0.10) 4px)
 *   + crt-flicker keyframes: opacity 0.95→1.0→0.98 at 150ms
 *
 * Skip on TrackScreen (Google Maps = performance route).
 * Non-clickable — placed at the top of the screen's z-stack.
 */
@Composable
fun CrtOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "crt_flicker")
    val alpha by transition.animateFloat(
        initialValue   = 0.95f,
        targetValue    = 1.00f,
        animationSpec  = infiniteRepeatable(
            animation   = tween(durationMillis = 150, easing = LinearEasing),
            repeatMode  = RepeatMode.Reverse,
        ),
        label = "crt_alpha",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                drawContent()
                // Scanline band every 4dp: 2dp transparent + 2dp dark
                val bandPx   = 4.dp.toPx()
                val darkBand = 2.dp.toPx()
                var y = 0f
                while (y < size.height) {
                    // Dark band (top half of each 4dp period)
                    drawRect(
                        color   = Color(0x1A000000),   // rgba(0,0,0,0.10) like web
                        topLeft = androidx.compose.ui.geometry.Offset(0f, y),
                        size    = androidx.compose.ui.geometry.Size(size.width, darkBand),
                        alpha   = alpha,
                    )
                    y += bandPx
                }
                // Vignette — radial darkening towards edges
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.35f),
                        ),
                        radius = size.minDimension * 1.2f,
                    ),
                )
            },
    )
}
