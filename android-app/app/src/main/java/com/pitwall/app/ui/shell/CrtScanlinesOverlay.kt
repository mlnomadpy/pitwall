package com.pitwall.app.ui.shell

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.pitwall.app.ui.theme.LocalPitwallReducedMotion

/** CRT scanlines from `App.vue` (`.crt-overlay`) — disabled when reduced-motion is on (matches PWA). */
@Composable
fun CrtScanlinesOverlay(modifier: Modifier = Modifier) {
    val reduced = LocalPitwallReducedMotion.current
    val transition = rememberInfiniteTransition(label = "crt_flicker")
    val flicker by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(150, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "flicker",
    )
    val alpha = if (reduced) 0f else flicker

    Canvas(modifier.fillMaxSize()) {
        if (alpha <= 0f) return@Canvas
        val lineSpacing = 4f * density
        var y = 0f
        while (y < size.height) {
            drawRect(
                color = Color.Black.copy(alpha = 0.08f * alpha),
                topLeft = Offset(0f, y),
                size = Size(size.width, lineSpacing * 0.45f),
            )
            y += lineSpacing
        }
    }
}
