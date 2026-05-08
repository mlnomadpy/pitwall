package com.pitwall.paddock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pitwall.paddock.ui.theme.ColorCharcoal
import com.pitwall.paddock.ui.theme.ColorSlate
import com.pitwall.paddock.ui.theme.Dimens

/**
 * Pitwall card frame — matches the web app's <Frame> component.
 *
 * Features:
 *   • ColorCharcoal (--color-charcoal) fill
 *   • ColorSlate/30 border at 1.5dp
 *   • Optional left accent bar (4dp wide, colored) — same as web variant="inset" left stripe
 *
 * Usage:
 *   PitwallFrame(accentColor = ColorUiBad) { ... }
 */
@Composable
fun PitwallFrame(
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    cornerRadius: androidx.compose.ui.unit.Dp = Dimens.CardCornerMd,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ColorCharcoal, shape)
            .border(
                width = Dimens.BorderThick,
                color = if (accentColor != null) accentColor.copy(alpha = 0.35f) else ColorSlate.copy(alpha = 0.25f),
                shape = shape,
            ),
    ) {
        if (accentColor != null) {
            // Left accent bar — same as web 4px colored strip
            Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                // Accent bar matches the height of the content due to IntrinsicSize.Min
                Box(
                    Modifier
                        .width(Dimens.AccentBarWidth)
                        .fillMaxHeight()
                        .background(
                            accentColor,
                            RoundedCornerShape(
                                topStart    = cornerRadius,
                                bottomStart = cornerRadius,
                                topEnd      = 0.dp,
                                bottomEnd   = 0.dp,
                            ),
                        ),
                )
                // Main content
                Box(Modifier.weight(1f).padding(start = Dimens.SpaceSm)) {
                    content()
                }
            }
        } else {
            Box(Modifier.padding(Dimens.SpaceMd)) {
                content()
            }
        }
    }
}
