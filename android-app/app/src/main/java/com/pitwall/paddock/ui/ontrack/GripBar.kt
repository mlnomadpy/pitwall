package com.pitwall.paddock.ui.ontrack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.ui.theme.ColorCharcoal
import com.pitwall.paddock.ui.theme.ColorUiBad
import com.pitwall.paddock.ui.theme.ColorUiGood
import com.pitwall.paddock.ui.theme.ColorUiWarn
import com.pitwall.paddock.ui.theme.OrbitronFamily

@Composable
fun GripBar(
    pct: Float,
    label: String,
    modifier: Modifier = Modifier
) {
    val barColor = when {
        pct > 95f -> ColorUiBad
        pct > 80f -> ColorUiWarn
        else -> ColorUiGood
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Canvas(modifier = Modifier.size(width = 24.dp, height = 120.dp)) {
            // Background
            drawRect(
                color = ColorCharcoal.copy(alpha = 0.5f),
                size = size
            )

            // Fill
            val fillHeight = size.height * (pct.coerceIn(0f, 100f) / 100f)
            drawRect(
                color = barColor,
                topLeft = Offset(0f, size.height - fillHeight),
                size = Size(size.width, fillHeight)
            )

            // Tick marks
            for (i in 1..4) {
                val y = size.height * (i / 5f)
                drawLine(
                    color = Color.Black.copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontFamily = OrbitronFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}
