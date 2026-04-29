package com.pitwall.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.app.data.CoachingMessage
import com.pitwall.app.data.CornerSessionStats
import com.pitwall.app.data.DriverInsight
import com.pitwall.app.data.TelemetryFrame
import com.pitwall.app.data.TrackOutline
import com.pitwall.app.ui.theme.MonoFamily
import com.pitwall.app.ui.theme.PitwallColors
import kotlin.math.*

// Mock lap data
private data class LapRow(val lap: Int, val timeS: Float, val bestDeltaS: Float)
private val MOCK_LAPS = listOf(
    LapRow(1, 102.4f,  0.0f),
    LapRow(2, 101.1f, -1.3f),
    LapRow(3, 100.8f, -0.3f),
    LapRow(4, 103.2f, +2.4f),
    LapRow(5,  99.6f, -1.2f),
)

@Composable
fun PaddockScreen(
    telemetry: TelemetryFrame?,
    lastCoaching: CoachingMessage?,
    laps: List<com.pitwall.app.service.LapRecord>,
    insights: List<DriverInsight>,
    insightsLoading: Boolean,
    insightsError: Boolean,
    cornerStats: Map<String, CornerSessionStats>,
    trackOutline: TrackOutline?,
    onReturnToTrack: () -> Unit,
    onRefreshInsights: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("LAPS", "INSIGHTS", "SPEED", "CORNERS", "FRICTION", "PROFILE")

    Column(Modifier.fillMaxSize().background(PitwallColors.Background)) {

        // ── Header ────────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().background(PitwallColors.Surface)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("PITWALL", fontWeight = FontWeight.Black, fontSize = 18.sp,
                color = PitwallColors.GripGreen, letterSpacing = 2.sp)
            Spacer(Modifier.width(8.dp))
            Text("ENGINEER MODE", color = PitwallColors.TextDim, fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            if (telemetry != null && telemetry.speedKmh > 10f) {
                IconButton(onClick = onReturnToTrack) {
                    Icon(Icons.Outlined.DirectionsCar, null, tint = PitwallColors.GripGreen)
                }
            }
        }

        // ── Tab Row ───────────────────────────────────────────────────────────
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = PitwallColors.Surface,
            contentColor = PitwallColors.GripGreen,
            edgePadding = 0.dp,
        ) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = selectedTab == i,
                    onClick = { selectedTab = i },
                    text = {
                        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    },
                    selectedContentColor = PitwallColors.GripGreen,
                    unselectedContentColor = PitwallColors.TextDim,
                )
            }
        }

        // ── Tab Content ───────────────────────────────────────────────────────
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                0 -> LapsTab(laps)
                1 -> InsightsTab(insights, insightsLoading, insightsError, onRefreshInsights)
                2 -> SpeedTraceTab(telemetry, trackOutline)
                3 -> CornersTab(cornerStats, trackOutline)
                4 -> FrictionTab(telemetry)
                5 -> ProfileTab(cornerStats)
            }
        }
    }
}

// ── LAPS ──────────────────────────────────────────────────────────────────────

@Composable
private fun LapsTab(laps: List<com.pitwall.app.service.LapRecord>) {
    val bestLap = laps.minByOrNull { it.timeS }
    val bestTimeStr = bestLap?.let {
        val mins = (it.timeS / 60).toInt()
        val secs = String.format("%04.1f", it.timeS % 60)
        "$mins:$secs"
    } ?: "--:--"
    
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        // Stat cards
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("BEST", bestTimeStr, Modifier.weight(1f))
            // We do not have an opponent benchmark configured right now, so we hide it.
            StatCard("LAPS", "${laps.size}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
        // Table header
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text("LAP", Modifier.weight(1f), color = PitwallColors.TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
            Text("TIME", Modifier.weight(1f), color = PitwallColors.TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
            Text("DELTA", Modifier.weight(1f), color = PitwallColors.TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
        }
        HorizontalDivider(color = PitwallColors.Border, modifier = Modifier.padding(vertical = 8.dp))
        laps.asReversed().forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp)) {
                Text("${row.lap}", Modifier.weight(1f), color = PitwallColors.TextPrimary, fontFamily = MonoFamily)
                val mins = (row.timeS / 60).toInt()
                val secs = String.format("%04.1f", row.timeS % 60)
                Text("$mins:$secs", Modifier.weight(1f), color = PitwallColors.TextPrimary, fontFamily = MonoFamily)
                val deltaColor = if (row.bestDeltaS < 0) PitwallColors.ThrottleGreen else if (row.bestDeltaS == 0f) PitwallColors.TextPrimary else PitwallColors.GripRed
                val sign = if (row.bestDeltaS > 0) "+" else ""
                Text("$sign${String.format("%.1f", row.bestDeltaS)}", Modifier.weight(1f), color = deltaColor, fontFamily = MonoFamily)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier, valueColor: Color = PitwallColors.TextPrimary) {
    Column(
        modifier.background(PitwallColors.Surface, RoundedCornerShape(10.dp)).padding(16.dp)
    ) {
        Text(label, color = PitwallColors.TextDim, fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = valueColor, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = MonoFamily)
    }
}

// ── INSIGHTS ──────────────────────────────────────────────────────────────────

@Composable
private fun InsightsTab(
    insights: List<DriverInsight>,
    loading: Boolean,
    error: Boolean,
    onRefresh: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("SESSION ANALYSIS", color = PitwallColors.TextDim, fontSize = 12.sp, letterSpacing = 2.sp)
            IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Refresh, "Refresh", tint = PitwallColors.TextDim)
            }
        }
        Spacer(Modifier.height(16.dp))

        if (error) {
            Text("Could not load insights. Is the bridge running?", color = PitwallColors.GripRed, fontSize = 14.sp)
            return@Column
        }

        if (loading && insights.isEmpty()) {
            repeat(3) { ShimmerInsightCard() }
        } else if (insights.isEmpty()) {
            Text("No insights generated yet. Complete more laps.", color = PitwallColors.TextDim, fontSize = 14.sp)
        } else {
            insights.sortedBy { it.rank }.take(3).forEach { insight ->
                InsightCard(insight)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ShimmerInsightCard() {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.2f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse)
    )
    Box(Modifier.fillMaxWidth().height(120.dp).padding(bottom = 12.dp)
        .background(PitwallColors.Surface.copy(alpha = alpha), RoundedCornerShape(10.dp)))
}

@Composable
private fun InsightCard(insight: DriverInsight) {
    val severityColor = when(insight.severity) {
        DriverInsight.Severity.HIGH -> PitwallColors.GripRed
        DriverInsight.Severity.MEDIUM -> PitwallColors.GripYellow
        DriverInsight.Severity.LOW -> PitwallColors.GripGreen
    }

    Column(Modifier.fillMaxWidth().background(PitwallColors.Surface, RoundedCornerShape(10.dp)).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("RANK ${insight.rank}", color = severityColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("-${String.format("%.1f", insight.estGainS)}s", color = PitwallColors.GripGreen, fontFamily = MonoFamily, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(insight.title, color = PitwallColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(insight.detail, color = PitwallColors.TextDim, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            insight.corners.take(3).forEach { corner ->
                Box(Modifier.background(PitwallColors.Background, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(corner.uppercase(), color = PitwallColors.TextDim, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── SPEED TRACE ───────────────────────────────────────────────────────────────

@Composable
private fun SpeedTraceTab(telemetry: TelemetryFrame?, trackOutline: TrackOutline?) {
    // Accumulate up to 300 speed values in a ring
    val speeds = remember { ArrayDeque<Float>(300) }
    LaunchedEffect(telemetry) {
        telemetry?.let {
            if (speeds.size >= 300) speeds.removeFirst()
            speeds.addLast(it.speedKmh)
        }
    }
    val speedSnapshot = speeds.toList()

    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        if (speedSnapshot.size < 2) {
            Text("Collecting data…", color = PitwallColors.TextDim, fontSize = 13.sp)
        } else {
            val rawMax = speedSnapshot.maxOrNull()?.coerceAtLeast(50f) ?: 200f
            // Snap max speed to next 20 for a cleaner Y-axis (e.g. 105 -> 120)
            val maxSpeed = ((rawMax / 20).toInt() + 1) * 20f

            Row(Modifier.fillMaxSize()) {
                // Y-axis labels (Speed km/h)
                Column(
                    Modifier.fillMaxHeight().width(32.dp).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text("${maxSpeed.toInt()}", color = PitwallColors.TextDim, fontSize = 10.sp)
                    Text("${(maxSpeed / 2).toInt()}", color = PitwallColors.TextDim, fontSize = 10.sp)
                    Text("0", color = PitwallColors.TextDim, fontSize = 10.sp)
                }

                Spacer(Modifier.width(8.dp))

                // Canvas and X-axis
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Box(
                        Modifier.weight(1f).fillMaxWidth().drawWithCache {
                            val path = Path()
                            speedSnapshot.forEachIndexed { i, v ->
                                val x = i / (speedSnapshot.size - 1f) * size.width
                                val y = size.height - (v / maxSpeed * size.height)
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }

                            // Dummy gold reference line: just a shifted version of the speed
                            val goldPath = Path()
                            speedSnapshot.forEachIndexed { i, v ->
                                val x = i / (speedSnapshot.size - 1f) * size.width
                                val goldSpeed = (v * 1.05f + 5f).coerceAtMost(maxSpeed) // limit to max
                                val y = size.height - (goldSpeed / maxSpeed * size.height)
                                if (i == 0) goldPath.moveTo(x, y) else goldPath.lineTo(x, y)
                            }

                            onDrawBehind {
                                // Background grid
                                val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                                val halfY = size.height / 2f
                                val halfX = size.width / 2f

                                // Horizontal grid lines
                                drawLine(PitwallColors.Border, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1f) // Top
                                drawLine(PitwallColors.Border.copy(alpha = 0.5f), Offset(0f, halfY), Offset(size.width, halfY), strokeWidth = 1f, pathEffect = dash) // Mid
                                drawLine(PitwallColors.Border, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f) // Bottom

                                // Vertical grid lines
                                drawLine(PitwallColors.Border.copy(alpha = 0.5f), Offset(halfX, 0f), Offset(halfX, size.height), strokeWidth = 1f, pathEffect = dash) // Mid

                                // Gold reference line
                                drawPath(goldPath, PitwallColors.GripYellow.copy(alpha = 0.5f), style = Stroke(width = 2.dp.toPx(), pathEffect = dash))

                                // Corner markers (dummy distribution for visual enhancement)
                                val numMarkers = trackOutline?.cornerMarkers?.size ?: 0
                                if (numMarkers > 0) {
                                    trackOutline?.cornerMarkers?.forEachIndexed { index, _ ->
                                        val x = (index.toFloat() / numMarkers) * size.width
                                        drawLine(PitwallColors.Border, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
                                    }
                                }

                                // Main speed trace
                                drawPath(path, PitwallColors.SpeedBlue, style = Stroke(width = 2.dp.toPx()))
                            }
                        }
                    )

                    // X-axis labels (Time)
                    Row(
                        Modifier.fillMaxWidth().height(24.dp).padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("-30s", color = PitwallColors.TextDim, fontSize = 10.sp)
                        Text("-15s", color = PitwallColors.TextDim, fontSize = 10.sp)
                        Text("Now", color = PitwallColors.TextDim, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

// ── CORNERS ───────────────────────────────────────────────────────────────────

@Composable
private fun CornersTab(cornerStats: Map<String, CornerSessionStats>, trackOutline: TrackOutline?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        if (cornerStats.isEmpty()) {
            Text("Collecting corner data…", color = PitwallColors.TextDim, fontSize = 13.sp)
        } else {
            // Sort by largest loss (totalDeltaKmh negative means observed is slower than ref)
            val sortedStats = cornerStats.values.sortedBy { it.totalDeltaKmh }
            sortedStats.forEach { stat ->
                val delta = stat.totalDeltaKmh
                val name = stat.name
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, Modifier.width(100.dp), color = PitwallColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(16.dp))
                    Box(Modifier.weight(1f).height(6.dp).background(PitwallColors.Border, RoundedCornerShape(3.dp))) {
                        // Map delta to fraction. Max difference ~ -20kmh to +20kmh
                        val fraction = ((delta + 20f) / 40f).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight()
                            .background(if (delta >= 0) PitwallColors.ThrottleGreen else PitwallColors.GripRed, RoundedCornerShape(3.dp)))
                    }
                    Spacer(Modifier.width(16.dp))
                    val sign = if (delta >= 0) "+" else ""
                    Text("$sign${String.format("%.1f", delta)} km/h", color = if (delta >= 0) PitwallColors.ThrottleGreen else PitwallColors.GripRed,
                        fontFamily = MonoFamily, fontSize = 13.sp, modifier = Modifier.width(76.dp))
                }
                // Detail row showing entry, apex, exit speeds
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp, start = 116.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("In: ${stat.observedEntryKmh.toInt()} (${stat.refEntryKmh.toInt()})", color = PitwallColors.TextDim, fontSize = 10.sp)
                    Text("Mid: ${stat.observedApexKmh.toInt()} (${stat.refApexKmh.toInt()})", color = PitwallColors.TextDim, fontSize = 10.sp)
                    Text("Out: ${stat.observedExitKmh.toInt()} (${stat.refExitKmh.toInt()})", color = PitwallColors.TextDim, fontSize = 10.sp)
                }
            }
        }
    }
}

// ── FRICTION ──────────────────────────────────────────────────────────────────

@Composable
private fun FrictionTab(telemetry: TelemetryFrame?) {
    data class GPoint(val lat: Float, val lon: Float)
    val points = remember { ArrayDeque<GPoint>(500) }
    LaunchedEffect(telemetry) {
        telemetry?.let {
            if (points.size >= 500) points.removeFirst()
            points.addLast(GPoint(it.gLat.value, it.gLong.value))
        }
    }
    val snapshot = points.toList()

    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(320.dp)) {
            // X-axis labels
            Text("-2.5G", Modifier.align(Alignment.CenterStart), color = PitwallColors.TextDim, fontSize = 10.sp)
            Text("+2.5G", Modifier.align(Alignment.CenterEnd), color = PitwallColors.TextDim, fontSize = 10.sp)
            
            // Y-axis labels
            Text("+2.5G", Modifier.align(Alignment.TopCenter), color = PitwallColors.TextDim, fontSize = 10.sp)
            Text("-2.5G", Modifier.align(Alignment.BottomCenter), color = PitwallColors.TextDim, fontSize = 10.sp)

            Box(Modifier.size(260.dp).align(Alignment.Center).drawWithCache {
                val cx = size.width / 2f; val cy = size.height / 2f
                val scale = size.width / 5f  // ±2.5G maps to full width
                onDrawBehind {
                    // Friction circle outline
                    drawCircle(PitwallColors.Border, radius = size.width / 2f, style = Stroke(1.dp.toPx()))
                    drawCircle(PitwallColors.Border.copy(alpha = 0.4f), radius = size.width / 4f, style = Stroke(1.dp.toPx()))
                    drawLine(PitwallColors.Border, Offset(cx, 0f), Offset(cx, size.height), 1.dp.toPx())
                    drawLine(PitwallColors.Border, Offset(0f, cy), Offset(size.width, cy), 1.dp.toPx())
                    // Dots
                    snapshot.forEach { p ->
                        val x = cx + p.lat * scale
                        val y = cy - p.lon * scale
                        val usage = sqrt(p.lat * p.lat + p.lon * p.lon) / 2.29f
                        val color = when {
                            usage > 1.05f -> PitwallColors.GripRed
                            usage > 0.90f -> PitwallColors.GripYellow
                            else          -> PitwallColors.GripGreen
                        }
                        drawCircle(color.copy(alpha = 0.6f), radius = 3.dp.toPx(), center = Offset(x, y))
                    }
                }
            })
        }
    }
}

// ── PROFILE ───────────────────────────────────────────────────────────────────

@Composable
private fun ProfileTab(cornerStats: Map<String, CornerSessionStats>) {
    val skills = if (cornerStats.isEmpty()) {
        listOf(
            "Braking Precision" to 0f,
            "Trail Braking"     to 0f,
            "Corner Entry Speed" to 0f,
            "Throttle Pickup"   to 0f,
            "Apex Commitment"   to 0f,
        )
    } else {
        val stats = cornerStats.values.filter { it.hasData }
        if (stats.isEmpty()) {
            listOf(
                "Braking Precision" to 0f,
                "Trail Braking"     to 0f,
                "Corner Entry Speed" to 0f,
                "Throttle Pickup"   to 0f,
                "Apex Commitment"   to 0f,
            )
        } else {
            val avgCoastPct = stats.map { it.coastPct }.average().toFloat()
            val brakingPrecision = (1f - (avgCoastPct / 30f)).coerceIn(0f, 1f)
            
            val avgTrailPct = stats.map { it.trailPct }.average().toFloat()
            val trailBraking = (avgTrailPct * 2f / 100f).coerceIn(0f, 1f) // e.g. 50% trail means 100% score
            
            val avgEntrySpeedRatio = stats.map { if (it.refEntryKmh > 0) it.observedEntryKmh / it.refEntryKmh else 1f }.average().toFloat()
            val entrySpeed = avgEntrySpeedRatio.coerceIn(0f, 1f)

            val avgApexSpeedRatio = stats.map { if (it.refApexKmh > 0) it.observedApexKmh / it.refApexKmh else 1f }.average().toFloat()
            val apexCommitment = avgApexSpeedRatio.coerceIn(0f, 1f)

            val avgExitSpeedRatio = stats.map { if (it.refExitKmh > 0) it.observedExitKmh / it.refExitKmh else 1f }.average().toFloat()
            val throttlePickup = avgExitSpeedRatio.coerceIn(0f, 1f)

            listOf(
                "Braking Precision" to brakingPrecision,
                "Trail Braking"     to trailBraking,
                "Corner Entry Speed" to entrySpeed,
                "Throttle Pickup"   to throttlePickup,
                "Apex Commitment"   to apexCommitment,
            )
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        skills.forEach { (name, score) ->
            Column {
                Row {
                    Text(name, Modifier.weight(1f), color = PitwallColors.TextPrimary, fontSize = 13.sp)
                    Text("${(score * 100).toInt()}%", color = PitwallColors.GripGreen, fontFamily = MonoFamily, fontSize = 13.sp)
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { score },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = PitwallColors.GripGreen,
                    trackColor = PitwallColors.Border,
                )
            }
        }
    }
}
