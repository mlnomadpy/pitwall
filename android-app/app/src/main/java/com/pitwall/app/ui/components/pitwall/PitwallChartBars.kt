package com.pitwall.app.ui.components.pitwall

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
fun PitwallHorizontalBar(
    label: String,
    fraction: Float,
    caption: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val track = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    Column(modifier.padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(track),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(5.dp))
                    .background(fill),
            )
        }
    }
}

/** Lap time (seconds) trend — faster laps toward the top of the chart. */
@Composable
fun LapTimeLineChart(
    lapTimesSeconds: List<Double>,
    modifier: Modifier =
        Modifier
            .fillMaxWidth()
            .height(140.dp),
) {
    val lineColor = MaterialTheme.colorScheme.primary
    if (lapTimesSeconds.size < 2) return
    val minT = lapTimesSeconds.minOrNull() ?: return
    val maxT = lapTimesSeconds.maxOrNull() ?: return
    val span = max(maxT - minT, 1e-6)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val pad = 8.dp.toPx()
        val plotW = w - pad * 2
        val plotH = h - pad * 2
        val n = lapTimesSeconds.size
        val path = Path()
        lapTimesSeconds.forEachIndexed { i, t ->
            val x = pad + plotW * (i.toFloat() / max(1, n - 1))
            val yn = ((t - minT) / span).toFloat()
            val y = pad + yn * plotH
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        lapTimesSeconds.forEachIndexed { i, _ ->
            val x = pad + plotW * (i.toFloat() / max(1, n - 1))
            val t = lapTimesSeconds[i]
            val yn = ((t - minT) / span).toFloat()
            val y = pad + yn * plotH
            drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
fun MiniSparkline(
    values: List<Double>,
    modifier: Modifier =
        Modifier
            .fillMaxWidth()
            .height(48.dp),
    /** When true, uses [MaterialTheme.colorScheme.primary] instead of tertiary. */
    usePrimary: Boolean = false,
) {
    val lineColor =
        if (usePrimary) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.tertiary
        }
    if (values.size < 2) return
    val minV = values.minOrNull() ?: return
    val maxV = values.maxOrNull() ?: return
    val span = max(maxV - minV, 1e-6)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = w * (i.toFloat() / max(1, values.size - 1))
            val yn = ((v - minV) / span).toFloat()
            val y = h - yn * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}
