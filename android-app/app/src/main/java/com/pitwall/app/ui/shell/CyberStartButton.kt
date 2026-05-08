package com.pitwall.app.ui.shell

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.app.ui.theme.LocalPitwallReducedMotion
import com.pitwall.app.ui.theme.PitwallFontTitle
import com.pitwall.app.ui.theme.PitwallPalette

/** Primary chamfered CTA echoing `CyberButton.vue` (`variant="primary"`, `size="lg"`). */
@Composable
fun CyberStartButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val density = LocalDensity.current
    val cutDp = 12.dp
    val shape =
        GenericShape { size, _ ->
            val c = with(density) { cutDp.toPx() }
            moveTo(c, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - c)
            lineTo(size.width - c, size.height)
            lineTo(0f, size.height)
            lineTo(0f, c)
            close()
        }

    val reduced = LocalPitwallReducedMotion.current
    val pulse = rememberInfiniteTransition(label = "cta_pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.88f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1100, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulse",
    )
    val glowAlpha = if (reduced) 1f else pulseAlpha

    Box(
        modifier =
            modifier
                .shadow(6.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .border(2.dp, PitwallPalette.UiGood, shape)
                .background(PitwallPalette.Ink)
                .clickable(enabled = enabled, onClick = onClick)
                .alpha(if (enabled) glowAlpha else 0.45f)
                .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "▶",
                color = Color.White,
                fontFamily = PitwallFontTitle,
                fontSize = 18.sp,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                label,
                color = Color.White,
                fontFamily = PitwallFontTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = 3.sp,
            )
        }
    }
}
