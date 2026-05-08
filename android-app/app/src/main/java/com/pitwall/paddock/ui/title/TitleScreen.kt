package com.pitwall.paddock.ui.title

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.ui.components.CrtOverlay
import com.pitwall.paddock.ui.components.CyberButton
import com.pitwall.paddock.ui.components.CyberButtonVariant
import com.pitwall.paddock.ui.components.GlowText
import com.pitwall.paddock.ui.theme.ColorInk
import com.pitwall.paddock.ui.theme.ColorUiGood
import com.pitwall.paddock.ui.theme.OrbitronFamily
import com.pitwall.paddock.ui.theme.RajdhaniFamily

@Composable
fun TitleScreen(
    onNavigateNext: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // A simple wanderer animation across the bottom
    val wandererTransition = rememberInfiniteTransition(label = "wanderer")
    val wandererX by wandererTransition.animateFloat(
        initialValue = -0.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wanderer_x"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorInk)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!pressed) {
                    pressed = true
                    onNavigateNext()
                }
            }
    ) {
        // CRT Background
        CrtOverlay(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Main Title
            GlowText(
                text = "PITWALL",
                color = Color.White,
                glowColor = ColorUiGood,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = OrbitronFamily,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 8.sp
                )
            )

            // Subtitle Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color(0x661A1D2E))
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Box(modifier = Modifier.size(6.dp).background(ColorUiGood))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "AI RACING COACH",
                    color = ColorUiGood,
                    fontFamily = RajdhaniFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.size(6.dp).background(ColorUiGood))
            }

            Spacer(modifier = Modifier.height(64.dp))

            // Start Button
            if (!pressed) {
                Box(modifier = Modifier.alpha(alpha)) {
                    CyberButton(
                        text = "SYSTEM BOOT",
                        onClick = onNavigateNext,
                        variant = CyberButtonVariant.Primary,
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(64.dp)
                    )
                }
            }
        }

        // Footer
        Text(
            text = "SONOMA RACEWAY · 2026 EDITION",
            color = Color.White.copy(alpha = 0.5f),
            fontFamily = RajdhaniFamily,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )

        // Wanderer
        Canvas(modifier = Modifier.fillMaxSize()) {
            val y = size.height - 100.dp.toPx()
            val x = size.width * wandererX
            drawCircle(
                color = Color.White.copy(alpha = 0.3f),
                radius = 8.dp.toPx(),
                center = Offset(x, y)
            )
        }

        // Flash on press
        if (pressed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.8f))
            )
        }
    }
}
