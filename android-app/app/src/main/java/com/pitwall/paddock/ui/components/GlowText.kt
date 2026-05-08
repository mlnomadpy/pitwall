package com.pitwall.paddock.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.NativeCanvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.pitwall.paddock.ui.theme.ColorUiGood
import com.pitwall.paddock.ui.theme.Dimens
import com.pitwall.paddock.ui.theme.OrbitronFamily
import androidx.compose.ui.draw.drawBehind

/**
 * Text with a soft paint-based glow behind it.
 *
 * Matches web text-glow-teal / text-glow-warn / text-glow-red utilities:
 *   text-shadow: 0 0 6px rgba(color, 0.8), 0 0 20px rgba(color, 0.3)
 *
 * Two draw passes:
 *   1. Blurred ghost layer (glow via setShadowLayer)
 *   2. Normal crisp text on top
 */
@Composable
fun GlowText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    glowColor: Color = ColorUiGood,
    glowRadius: Float = Dimens.GlowRadiusMd,
    style: TextStyle = TextStyle(fontFamily = OrbitronFamily, fontWeight = FontWeight.Bold),
) {
    Box(modifier = modifier) {
        // Glow layer
        Text(
            text  = text,
            color = glowColor.copy(alpha = 0.5f),
            style = style,
            modifier = Modifier.drawBehind {
                drawIntoCanvas { canvas ->
                    val paint = Paint().also { p ->
                        p.asFrameworkPaint().apply {
                            isAntiAlias = true
                            this.color = android.graphics.Color.TRANSPARENT
                            setShadowLayer(glowRadius * 1.5f, 0f, 0f, glowColor.copy(alpha = 0.7f).toArgb())
                        }
                    }
                    canvas.drawRect(
                        left   = -glowRadius,
                        top    = -glowRadius,
                        right  = size.width  + glowRadius,
                        bottom = size.height + glowRadius,
                        paint  = paint,
                    )
                }
            },
        )
        // Crisp text on top
        Text(
            text  = text,
            color = color,
            style = style,
        )
    }
}
