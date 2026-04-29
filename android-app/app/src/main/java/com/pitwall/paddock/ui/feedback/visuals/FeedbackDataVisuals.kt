package com.pitwall.paddock.ui.feedback.visuals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.data.AnalysisVisualsMock
import com.pitwall.paddock.data.AnalysisVisualsMock.DriverStints
import com.pitwall.paddock.ui.theme.AccentGreen
import com.pitwall.paddock.ui.theme.AccentRed
import com.pitwall.paddock.ui.theme.CardStroke
import com.pitwall.paddock.ui.theme.PitwallBg
import com.pitwall.paddock.ui.theme.PitwallCyan
import com.pitwall.paddock.ui.theme.PitwallSurface
import com.pitwall.paddock.ui.theme.TextPrimary
import com.pitwall.paddock.ui.theme.TextSecondary

@Composable
fun FeedbackDataVisualsSection() {
    val speedA = remember { AnalysisVisualsMock.speedTraceA() }
    val speedB = remember { AnalysisVisualsMock.speedTraceB() }
    val lapTimes = remember { AnalysisVisualsMock.lapTimeTrend() }
    val sectorRows = remember { AnalysisVisualsMock.sectorDeltasGrid() }
    val tire = remember { AnalysisVisualsMock.tireStrategy() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "TELEMETRY-STYLE PREVIEWS (MOCK — REPLACE WITH API)",
            color = TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
        )
        MockPlaybackStrip()
        VisualCard("Speed trace · distance (primary vs reference)") {
            PitwallMultiLineChart(
                seriesA = speedA,
                seriesB = speedB,
                colorA = Color(0xFF42A5F5),
                colorB = Color(0xFFFF9800),
                labelA = "You",
                labelB = "Ref",
                yLabel = "km/h",
                xLabel = "m",
            )
        }
        VisualCard("Lap time trend (last 10 laps)") {
            SingleSeriesLineChart(
                points = lapTimes.map { it.first.toFloat() to it.second },
                lineColor = PitwallCyan,
                yLabel = "s",
                xLabel = "lap",
            )
        }
        VisualCard("Tire strategy (stint bars)") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tire.forEach { row ->
                    TireStintRow(row, totalRaceLaps = 48)
                }
            }
        }
        VisualCard("Sector Δ vs reference (s)") {
            SectorDeltaTable(sectorRows)
        }
    }
}

@Composable
private fun MockPlaybackStrip() {
    var position by remember { mutableFloatStateOf(0.35f) }
    var playing by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(PitwallSurface, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Historical playback", color = TextSecondary, fontSize = 10.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "4×",
                    color = PitwallCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "01:56:09",
                color = TextPrimary,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = { playing = !playing },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    "Play",
                    tint = PitwallCyan,
                )
            }
            Text("Speed", color = TextSecondary, fontSize = 9.sp, modifier = Modifier.width(32.dp))
            Slider(
                value = position,
                onValueChange = { position = it },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = PitwallCyan,
                    activeTrackColor = PitwallCyan,
                    inactiveTrackColor = CardStroke,
                ),
            )
        }
    }
}

@Composable
private fun VisualCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(PitwallBg, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Text(
            title,
            color = PitwallCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun PitwallMultiLineChart(
    seriesA: List<Pair<Float, Float>>,
    seriesB: List<Pair<Float, Float>>?,
    colorA: Color,
    colorB: Color,
    labelA: String,
    labelB: String?,
    yLabel: String,
    xLabel: String,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot(colorA, labelA)
            if (labelB != null) LegendDot(colorB, labelB)
        }
        Spacer(Modifier.height(4.dp))
        LineChartCanvas(
            seriesA = seriesA,
            seriesB = seriesB,
            colorA = colorA,
            colorB = colorB,
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0 $xLabel", color = TextSecondary, fontSize = 8.sp)
            Text("lap distance →", color = TextSecondary, fontSize = 8.sp)
        }
        Text("↑ $yLabel", color = TextSecondary, fontSize = 8.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun LegendDot(c: Color, name: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(c, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(name, color = TextPrimary, fontSize = 9.sp)
    }
}

@Composable
private fun SingleSeriesLineChart(
    points: List<Pair<Float, Float>>,
    lineColor: Color,
    yLabel: String,
    xLabel: String,
) {
    Column {
        LineChartCanvas(seriesA = points, seriesB = null, colorA = lineColor, colorB = lineColor)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1 $xLabel", color = TextSecondary, fontSize = 8.sp)
            Text("→", color = TextSecondary, fontSize = 8.sp)
        }
        Text(yLabel, color = TextSecondary, fontSize = 8.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun LineChartCanvas(
    seriesA: List<Pair<Float, Float>>,
    seriesB: List<Pair<Float, Float>>?,
    colorA: Color,
    colorB: Color,
) {
    val all = seriesA + (seriesB.orEmpty())
    if (all.isEmpty()) return
    val xMin = all.minOf { it.first }
    val xMax = all.maxOf { it.first }
    val yMin = all.minOf { it.second }
    val yMax = all.maxOf { it.second }
    val xSpan = (xMax - xMin).coerceAtLeast(1f)
    val ySpan = (yMax - yMin).coerceAtLeast(1f)
    val grid = CardStroke
    Canvas(Modifier.height(150.dp).fillMaxWidth()) {
        val w = size.width
        val h = size.height
        val pad = 4f
        for (g in 0..4) {
            val y = pad + (h - 2 * pad) * g / 4f
            drawLine(color = grid.copy(alpha = 0.35f), start = Offset(pad, y), end = Offset(w - pad, y), strokeWidth = 0.8f)
        }
        for (g in 0..6) {
            val x = pad + (w - 2 * pad) * g / 6f
            drawLine(color = grid.copy(alpha = 0.2f), start = Offset(x, pad), end = Offset(x, h - pad), strokeWidth = 0.6f)
        }
        fun pathOf(pts: List<Pair<Float, Float>>): Path {
            val p = Path()
            pts.forEachIndexed { i, d ->
                val tx = (d.first - xMin) / xSpan
                val ty = (d.second - yMin) / ySpan
                val x = pad + (w - 2 * pad) * tx
                val yy = h - pad - (h - 2 * pad) * ty
                if (i == 0) p.moveTo(x, yy) else p.lineTo(x, yy)
            }
            return p
        }
        drawPath(pathOf(seriesA), colorA, style = Stroke(width = 2.2f, cap = StrokeCap.Round))
        seriesB?.let {
            drawPath(pathOf(it), colorB, style = Stroke(width = 2.2f, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun TireStintRow(
    row: DriverStints,
    totalRaceLaps: Int,
) {
    val total = row.stints.sumOf { it.laps }.coerceAtLeast(1)
    val scale = totalRaceLaps / total.toFloat()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            row.code,
            color = TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp),
        )
        Row(Modifier.height(20.dp).weight(1f)) {
            row.stints.forEach { s ->
                val c = AnalysisVisualsMock.compoundColor(s.compound)
                val w = (s.laps * scale).coerceAtLeast(1f)
                Box(
                    Modifier
                        .weight(w)
                        .height(20.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(c.copy(alpha = 0.9f), c.copy(alpha = 0.6f)),
                            ),
                        )
                        .padding(horizontal = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (s.laps >= 8) {
                        Text(
                            "${s.laps}",
                            color = Color.Black,
                            fontSize = 7.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectorDeltaTable(
    rows: List<AnalysisVisualsMock.DriverSectorDeltas>,
) {
    val scroll = rememberScrollState()
    Row(Modifier.horizontalScroll(scroll)) {
        Column {
            Row(Modifier.background(PitwallSurface)) {
                CellH("Driver", 40.dp)
                CellH("S1 Δ", 44.dp)
                CellH("S2 Δ", 44.dp)
                CellH("S3 Δ", 44.dp)
            }
            rows.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.code,
                        color = TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(40.dp)
                            .background(r.teamColor.copy(alpha = 0.35f))
                            .padding(vertical = 4.dp)
                            .padding(horizontal = 2.dp),
                    )
                    DeltaCell(r.s1Delta, 44.dp)
                    DeltaCell(r.s2Delta, 44.dp)
                    DeltaCell(r.s3Delta, 44.dp)
                }
            }
        }
    }
    Text("Green = gain vs ref · red = loss", color = TextSecondary, fontSize = 8.sp, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun RowScope.CellH(text: String, w: androidx.compose.ui.unit.Dp) {
    Text(
        text,
        color = TextSecondary,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.width(w).padding(4.dp),
    )
}

@Composable
private fun RowScope.DeltaCell(delta: Float, w: androidx.compose.ui.unit.Dp) {
    val faster = delta < -0.0001f
    val slower = delta > 0.0001f
    val bg = when {
        faster -> AccentGreen.copy(alpha = 0.28f)
        slower -> AccentRed.copy(alpha = 0.28f)
        else -> PitwallSurface
    }
    val fg = when {
        faster -> AccentGreen
        slower -> AccentRed
        else -> TextPrimary
    }
    val s = (if (delta >= 0) "+" else "") + String.format("%.3f", delta)
    Text(
        s,
        color = fg,
        fontSize = 9.sp,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.End,
        modifier = Modifier
            .width(w)
            .background(bg)
            .padding(4.dp),
    )
}
