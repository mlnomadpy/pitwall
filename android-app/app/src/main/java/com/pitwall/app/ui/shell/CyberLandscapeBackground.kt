package com.pitwall.app.ui.shell

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pitwall.app.ui.theme.LocalPitwallReducedMotion
import com.pitwall.app.ui.theme.PitwallPalette

/** Landscape hero background from `CyberBackground.vue` (`variant="landscape"`). */
@Composable
fun CyberLandscapeBackground(modifier: Modifier = Modifier) {
    val reduced = LocalPitwallReducedMotion.current
    val transition = rememberInfiniteTransition(label = "star_twinkle")
    val twinkle by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.95f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "twinkle",
    )
    val starAlpha = if (reduced) 0.72f else twinkle

    BoxWithConstraints(modifier.fillMaxSize()) {
        val trackFrac = 0.22f
        val trackH = maxHeight * trackFrac

        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops =
                                arrayOf(
                                    0f to Color(0xFF0d0e1a),
                                    0.25f to Color(0xFF1a1d3e),
                                    0.45f to Color(0xFF3a2a4a),
                                    0.65f to Color(0xFFc8786a),
                                    0.78f to Color(0xFFd8b878),
                                    0.79f to Color(0xFF2c3242),
                                    1f to Color(0xFF1f2230),
                                ),
                        ),
                    ),
            )

            Canvas(Modifier.fillMaxSize()) {
                val dots =
                    listOf(
                        Offset(size.width * 0.1f, size.height * 0.08f),
                        Offset(size.width * 0.25f, size.height * 0.05f),
                        Offset(size.width * 0.42f, size.height * 0.12f),
                        Offset(size.width * 0.72f, size.height * 0.06f),
                        Offset(size.width * 0.88f, size.height * 0.11f),
                        Offset(size.width * 0.55f, size.height * 0.04f),
                    )
                val dots2 =
                    listOf(
                        Offset(size.width * 0.18f, size.height * 0.14f),
                        Offset(size.width * 0.63f, size.height * 0.09f),
                        Offset(size.width * 0.92f, size.height * 0.18f),
                    )
                for (p in dots) {
                    drawCircle(color = Color.White.copy(alpha = 0.55f * starAlpha), radius = 1.2f, center = p)
                }
                for (p in dots2) {
                    drawCircle(color = Color.White.copy(alpha = 0.38f * starAlpha), radius = 1.5f, center = p)
                }
            }

            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp),
                ) {
                    val stripeW = size.width * 0.03f
                    var x = 0f
                    var red = true
                    while (x < size.width) {
                        drawRect(
                            color = if (red) PitwallPalette.CurbRed else PitwallPalette.CurbWhite,
                            topLeft = Offset(x, 0f),
                            size = Size(stripeW, size.height),
                        )
                        x += stripeW
                        red = !red
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(trackH)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF2c3242),
                                    Color(0xFF1f2230),
                                ),
                            ),
                        ),
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stripe = size.width * 0.08f
                        var xx = 0f
                        while (xx < size.width) {
                            drawRect(
                                color = Color.White.copy(alpha = 0.03f),
                                topLeft = Offset(xx, 0f),
                                size = Size(stripe, size.height),
                            )
                            xx += stripe * 2
                        }
                    }
                }
            }
        }
    }
}
