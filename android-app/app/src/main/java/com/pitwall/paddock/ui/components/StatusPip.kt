package com.pitwall.paddock.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import com.pitwall.paddock.ui.theme.ColorUiGood
import com.pitwall.paddock.ui.theme.Dimens

/**
 * Pulsing status pip — matches web .status-pip.active animation.
 *
 * Active:   scale pulses 1.0 → 0.8 → 1.0 at 2s, colored
 * Inactive: static, ColorUiBad (red) or dim slate
 */
@Composable
fun StatusPip(
    active: Boolean,
    color: Color = ColorUiGood,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "pip_pulse")
    val scale by transition.animateFloat(
        initialValue  = 1f,
        targetValue   = 0.75f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pip_scale",
    )

    Box(
        modifier = modifier
            .size(Dimens.PipSize)
            .scale(if (active) scale else 1f)
            .background(
                color = if (active) color else color.copy(alpha = 0.3f),
                shape = RoundedCornerShape(Dimens.PipCorner),
            ),
    )
}
