package com.pitwall.paddock.ui.ontrack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pitwall.paddock.data.TrackOutline
import com.pitwall.paddock.ui.theme.ColorUiGood
import com.pitwall.paddock.ui.theme.ColorSlate

@Composable
fun SonomaMiniMap(
    outline: TrackOutline?,
    distanceM: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (outline == null || outline.points.isEmpty()) return@Canvas

        val pts = outline.points
        
        // Normalize points to fit in the Canvas
        val minX = pts.minOf { it.x }
        val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }
        val maxY = pts.maxOf { it.y }
        
        val w = maxX - minX
        val h = maxY - minY
        
        // Add padding
        val padding = 20.dp.toPx()
        val drawW = size.width - padding * 2
        val drawH = size.height - padding * 2
        
        val scale = minOf(drawW / w, drawH / h)
        val xOffset = (size.width - w * scale) / 2 - minX * scale
        val yOffset = (size.height - h * scale) / 2 - minY * scale

        // Draw track outline
        val path = Path()
        pts.forEachIndexed { index, p ->
            val px = p.x * scale + xOffset
            val py = p.y * scale + yOffset
            if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()

        drawPath(
            path = path,
            color = ColorSlate.copy(alpha = 0.4f),
            style = Stroke(
                width = 8.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Find position dot based on distanceM
        val dotPoint = outline.distanceToPoint(distanceM)
        val dotX = dotPoint.x * scale + xOffset
        val dotY = dotPoint.y * scale + yOffset

        drawCircle(
            color = ColorUiGood,
            radius = 6.dp.toPx(),
            center = Offset(dotX, dotY)
        )
    }
}
