package com.pitwall.paddock.ui.feedback

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.LapRecord
import com.pitwall.paddock.data.CornerSessionStats
import com.pitwall.paddock.data.DriverInsight
import com.pitwall.paddock.data.TrackOutline
import com.pitwall.paddock.ui.components.CrtOverlay
import com.pitwall.paddock.ui.components.KerbStripe
import com.pitwall.paddock.ui.components.PitwallFrame
import com.pitwall.paddock.ui.components.PitwallTopBar
import com.pitwall.paddock.ui.theme.ColorBiosGreen
import com.pitwall.paddock.ui.theme.ColorCharcoal
import com.pitwall.paddock.ui.theme.ColorInk
import com.pitwall.paddock.ui.theme.ColorSlate
import com.pitwall.paddock.ui.theme.ColorSilver
import com.pitwall.paddock.ui.theme.ColorUiBad
import com.pitwall.paddock.ui.theme.ColorUiGood
import com.pitwall.paddock.ui.theme.ColorUiWarn
import com.pitwall.paddock.ui.theme.Dimens
import com.pitwall.paddock.ui.theme.OrbitronFamily
import com.pitwall.paddock.ui.theme.RajdhaniFamily
import com.pitwall.paddock.ui.theme.ShareTechMonoFamily
import kotlin.math.sqrt

@Composable
fun PostSessionAnalysisScreen(
    laps: List<LapRecord>,
    insights: List<DriverInsight>,
    insightsLoading: Boolean,
    insightsError: Boolean,
    cornerStats: Map<String, CornerSessionStats>,
    trackOutline: TrackOutline?,
    useMph: Boolean,
    onRefreshInsights: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("LAPS", "INSIGHTS", "SPEED", "CORNERS", "FRICTION", "PROFILE")

    Column(
        Modifier
            .fillMaxSize()
            .background(ColorInk),
    ) {
        PitwallTopBar(title = "ENGINEER MODE")
        KerbStripe()

        // ── Scrollable Tab Row ────────────────────────────────────────────────
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor   = ColorCharcoal,
            contentColor     = ColorUiGood,
            edgePadding      = 0.dp,
            indicator        = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    Box(
                        Modifier
                            .tabIndicatorOffset(tabPositions[selectedTab])
                            .height(2.dp)
                            .background(ColorUiGood),
                    )
                }
            },
            divider = {},
        ) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected             = selectedTab == i,
                    onClick              = { selectedTab = i },
                    selectedContentColor = ColorUiGood,
                    unselectedContentColor = ColorSlate.copy(alpha = 0.5f),
                    text = {
                        Text(
                            title,
                            fontFamily    = RajdhaniFamily,
                            fontWeight    = FontWeight.Bold,
                            fontSize      = 10.sp,
                            letterSpacing = 1.sp,
                        )
                    },
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                0 -> LapsTab(laps)
                1 -> InsightsTab(insights, insightsLoading, insightsError, onRefreshInsights)
                2 -> SpeedTab(trackOutline, useMph)
                3 -> CornersTab(cornerStats, useMph)
                4 -> FrictionTab()
                5 -> ProfileTab(cornerStats)
            }
            CrtOverlay()
        }
    }
}

// ── TAB: Laps ─────────────────────────────────────────────────────────────────

@Composable
private fun LapsTab(laps: List<LapRecord>) {
    val bestLap = laps.minByOrNull { it.timeS }
    val bestTimeStr = bestLap?.let {
        "${(it.timeS / 60).toInt()}:${String.format("%04.1f", it.timeS % 60)}"
    } ?: "--:--"

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnalysisStatCard("BEST LAP", bestTimeStr,    Modifier.weight(1f))
            AnalysisStatCard("LAPS",     "${laps.size}", Modifier.weight(1f))
        }

        Spacer(Modifier.height(Dimens.SpaceLg))
        TabSectionHeader("LAP HISTORY")
        Spacer(Modifier.height(8.dp))

        PitwallFrame {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
                    LapHeader("LAP",   Modifier.width(36.dp))
                    LapHeader("TIME",  Modifier.weight(1f))
                    LapHeader("DELTA", Modifier.weight(1f))
                }
                HorizontalDivider(
                    color     = ColorSlate.copy(alpha = 0.2f),
                    modifier  = Modifier.padding(vertical = 8.dp),
                    thickness = Dimens.BorderNormal,
                )
                if (laps.isEmpty()) {
                    Text("No laps recorded yet.", color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                } else {
                    laps.asReversed().forEach { row ->
                        LapRow(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun LapHeader(text: String, modifier: Modifier) {
    Text(text, modifier, color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
}

@Composable
private fun LapRow(row: LapRecord) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp, horizontal = 2.dp),
    ) {
        Text("${row.lap}", color = ColorUiGood, fontFamily = OrbitronFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(36.dp))
        val mins = (row.timeS / 60).toInt()
        val secs = String.format("%04.1f", row.timeS % 60)
        Text("$mins:$secs", color = ColorSilver, fontFamily = ShareTechMonoFamily, fontSize = 12.sp, modifier = Modifier.weight(1f))
        val c    = if (row.bestDeltaS < 0) ColorBiosGreen else if (row.bestDeltaS == 0f) ColorSlate else ColorUiBad
        val sign = if (row.bestDeltaS > 0) "+" else ""
        Text("$sign${String.format("%.1f", row.bestDeltaS)}", color = c, fontFamily = ShareTechMonoFamily, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

// ── TAB: Insights ─────────────────────────────────────────────────────────────

@Composable
private fun InsightsTab(
    insights: List<DriverInsight>,
    loading: Boolean,
    error: Boolean,
    onRefresh: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TabSectionHeader("SESSION ANALYSIS")
            IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Refresh, "Refresh", tint = ColorSlate)
            }
        }

        if (error) {
            Text("Bridge offline. Run pitwall_bridge.py and tap refresh.", color = ColorUiBad, fontFamily = RajdhaniFamily, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            return@Column
        }

        if (loading && insights.isEmpty()) {
            repeat(2) {
                ShimmerCard()
                Spacer(Modifier.height(8.dp))
            }
        } else if (insights.isEmpty()) {
            Text("No insights yet. Complete a lap and tap refresh.", color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        } else {
            val grouped = insights.groupBy { it.lap }.toSortedMap(compareByDescending { it })
            grouped.forEach { (lap, lapInsights) ->
                Text("LAP $lap", color = ColorUiGood, fontFamily = OrbitronFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                lapInsights.sortedBy { it.rank }.forEach { insight ->
                    InsightCard(insight)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ShimmerCard() {
    val t = rememberInfiniteTransition(label = "shimmer")
    val a by t.animateFloat(0.15f, 0.5f, infiniteRepeatable(tween(900), RepeatMode.Reverse), "alpha")
    Box(
        Modifier
            .fillMaxWidth()
            .height(110.dp)
            .background(ColorCharcoal.copy(alpha = a), RoundedCornerShape(Dimens.CardCornerMd)),
    )
}

@Composable
private fun InsightCard(insight: DriverInsight) {
    val (sColor, sLabel) = when (insight.severity) {
        DriverInsight.Severity.HIGH   -> ColorUiBad   to "HIGH"
        DriverInsight.Severity.MEDIUM -> ColorUiWarn  to "MED"
        DriverInsight.Severity.LOW    -> ColorBiosGreen to "LOW"
    }
    PitwallFrame(accentColor = sColor) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.border(0.5.dp, sColor, RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Text("RANK ${insight.rank}  ·  $sLabel", color = sColor, fontFamily = RajdhaniFamily, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    }
                }
                Text("-${String.format("%.1f", insight.estGainS)}s", color = ColorBiosGreen, fontFamily = ShareTechMonoFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(insight.title, color = ColorSilver, fontFamily = OrbitronFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(insight.detail, color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
            if (insight.corners.isNotEmpty()) {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    insight.corners.take(3).forEach { corner ->
                        Box(Modifier.background(ColorInk, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(corner.uppercase(), color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ── TAB: Speed trace ──────────────────────────────────────────────────────────

@Composable
private fun SpeedTab(trackOutline: TrackOutline?, useMph: Boolean) {
    val label = if (useMph) "mph" else "km/h"
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TabSectionHeader("SPEED TRACE")
        Spacer(Modifier.height(Dimens.SpaceMd))
        PitwallFrame {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .drawWithCache {
                        val dash    = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                        val maxV    = if (useMph) 120f else 200f
                        val numPts  = 100
                        val driverPath = Path()
                        val refPath    = Path()
                        for (i in 0 until numPts) {
                            val t      = i / (numPts - 1f)
                            val x      = t * size.width
                            // Synthetic sine-wave stand-in — replaced by live data in prod
                            val speed  = maxV * 0.5f * (1f + kotlin.math.sin(t * 6 * Math.PI).toFloat())
                            val refSpd = (speed * 1.04f).coerceAtMost(maxV)
                            val y      = size.height - (speed / maxV * size.height)
                            val yr     = size.height - (refSpd / maxV * size.height)
                            if (i == 0) { driverPath.moveTo(x, y); refPath.moveTo(x, yr) }
                            else        { driverPath.lineTo(x, y); refPath.lineTo(x, yr) }
                        }
                        onDrawBehind {
                            drawLine(color = ColorSlate.copy(alpha = 0.2f), start = Offset(0f, 0f), end = Offset(size.width, 0f), strokeWidth = 1f)
                            drawLine(color = ColorSlate.copy(alpha = 0.1f), start = Offset(0f, size.height / 2f), end = Offset(size.width, size.height / 2f), strokeWidth = 1f, pathEffect = dash)
                            drawLine(color = ColorSlate.copy(alpha = 0.2f), start = Offset(0f, size.height), end = Offset(size.width, size.height), strokeWidth = 1f)
                            drawPath(refPath,    ColorUiWarn.copy(alpha = 0.5f), style = Stroke(1.5.dp.toPx(), pathEffect = dash))
                            drawPath(driverPath, ColorUiGood, style = Stroke(2.dp.toPx()))
                        }
                    },
            )
        }
        Spacer(Modifier.height(Dimens.SpaceSm))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(ColorUiGood,  "Current  ($label)")
            LegendDot(ColorUiWarn,  "Benchmark ($label)", dashed = true)
        }
    }
}

// ── TAB: Corners ──────────────────────────────────────────────────────────────

@Composable
private fun CornersTab(cornerStats: Map<String, CornerSessionStats>, useMph: Boolean) {
    val factor = if (useMph) 0.621371f else 1f
    val unit   = if (useMph) "mph" else "km/h"

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        TabSectionHeader("CORNER SPEED ANALYSIS")
        Spacer(Modifier.height(Dimens.SpaceMd))

        if (cornerStats.isEmpty()) {
            Text("Collecting corner data…", color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 13.sp)
        } else {
            cornerStats.values.sortedBy { it.totalDeltaKmh }.forEach { stat ->
                val delta    = stat.totalDeltaKmh * factor
                val deltaClr = if (delta >= 0) ColorBiosGreen else ColorUiBad
                Column(Modifier.padding(bottom = 14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stat.name, Modifier.width(90.dp), color = ColorSilver, fontFamily = RajdhaniFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Box(Modifier.weight(1f).height(6.dp).background(ColorSlate.copy(alpha = 0.15f), RoundedCornerShape(3.dp))) {
                            val fraction = ((delta + 20f) / 40f).coerceIn(0f, 1f)
                            Box(Modifier.fillMaxWidth(fraction).fillMaxSize().background(deltaClr, RoundedCornerShape(3.dp)))
                        }
                        val sign = if (delta >= 0) "+" else ""
                        Text("$sign${String.format("%.1f", delta)}", Modifier.width(70.dp).padding(start = 8.dp), color = deltaClr, fontFamily = ShareTechMonoFamily, fontSize = 11.sp)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 3.dp, start = 90.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        CornerSpeedLabel("In",  stat.observedEntryKmh * factor, stat.refEntryKmh * factor, unit)
                        CornerSpeedLabel("Mid", stat.observedApexKmh  * factor, stat.refApexKmh  * factor, unit)
                        CornerSpeedLabel("Out", stat.observedExitKmh  * factor, stat.refExitKmh  * factor, unit)
                    }
                }
            }
        }
    }
}

@Composable
private fun CornerSpeedLabel(label: String, observed: Float, ref: Float, unit: String) {
    Text("$label: ${observed.toInt()} (${ref.toInt()} $unit)", color = ColorSlate, fontFamily = ShareTechMonoFamily, fontSize = 9.sp)
}

// ── TAB: Friction G-G ─────────────────────────────────────────────────────────

@Composable
private fun FrictionTab() {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TabSectionHeader("FRICTION CIRCLE")
            Spacer(Modifier.height(Dimens.SpaceMd))
            GGCircleCanvas()
            Spacer(Modifier.height(Dimens.SpaceMd))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(ColorBiosGreen, "Normal grip")
                LegendDot(ColorUiWarn,   "Near limit")
                LegendDot(ColorUiBad,    "Over limit")
            }
        }
    }
}

@Composable
private fun GGCircleCanvas() {
    val canvasModifier = Modifier
        .fillMaxWidth(0.75f)
        .aspectRatio(1f)
    Canvas(canvasModifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r  = cx * 0.9f
        drawCircle(ColorSlate.copy(alpha = 0.25f), r, center = Offset(cx, cy), style = Stroke(2f))
        drawCircle(ColorSlate.copy(alpha = 0.12f), r * 0.5f, center = Offset(cx, cy), style = Stroke(1f))
        drawLine(color = ColorSlate.copy(alpha = 0.2f), start = Offset(cx, cy - r), end = Offset(cx, cy + r), strokeWidth = 1f)
        drawLine(color = ColorSlate.copy(alpha = 0.2f), start = Offset(cx - r, cy), end = Offset(cx + r, cy), strokeWidth = 1f)
        val scale = r / 2.5f
        for (i in 0..60) {
            val angle  = i * 0.35f
            val gLat   = kotlin.math.sin(angle.toDouble()).toFloat() * 1.6f
            val gLong  = kotlin.math.cos(angle.toDouble() * 1.3).toFloat() * 1.2f
            val usage  = sqrt(gLat * gLat + gLong * gLong) / 2.29f
            val dotClr = when {
                usage > 1.05f -> ColorUiBad
                usage > 0.90f -> ColorUiWarn
                else          -> ColorBiosGreen
            }
            drawCircle(dotClr.copy(alpha = 0.6f), 6f, Offset(cx + gLat * scale, cy - gLong * scale))
        }
    }
}



// ── TAB: Driver profile ───────────────────────────────────────────────────────

@Composable
private fun ProfileTab(cornerStats: Map<String, CornerSessionStats>) {
    val stats = cornerStats.values.filter { it.hasData }
    val skills = if (stats.isEmpty()) {
        listOf("Braking Precision" to 0f, "Trail Braking" to 0f, "Corner Entry" to 0f, "Throttle Pickup" to 0f, "Apex Commitment" to 0f)
    } else {
        val avgCoast  = stats.map { it.coastPct }.average().toFloat()
        val avgTrail  = stats.map { it.trailPct }.average().toFloat()
        val entryR    = stats.map { if (it.refEntryKmh > 0) it.observedEntryKmh / it.refEntryKmh else 1f }.average().toFloat()
        val apexR     = stats.map { if (it.refApexKmh  > 0) it.observedApexKmh  / it.refApexKmh  else 1f }.average().toFloat()
        val exitR     = stats.map { if (it.refExitKmh  > 0) it.observedExitKmh  / it.refExitKmh  else 1f }.average().toFloat()
        listOf(
            "Braking Precision"  to (1f - (avgCoast / 30f)).coerceIn(0f, 1f),
            "Trail Braking"      to (avgTrail * 2f / 100f).coerceIn(0f, 1f),
            "Corner Entry Speed" to entryR.coerceIn(0f, 1f),
            "Throttle Pickup"    to exitR.coerceIn(0f, 1f),
            "Apex Commitment"    to apexR.coerceIn(0f, 1f),
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TabSectionHeader("DRIVER PROFILE")
        skills.forEach { (name, score) ->
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Text(name, Modifier.weight(1f), color = ColorSilver, fontFamily = RajdhaniFamily, fontSize = 13.sp)
                    Text("${(score * 100).toInt()}%", color = ColorUiGood, fontFamily = ShareTechMonoFamily, fontSize = 13.sp)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress   = { score },
                    modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color      = ColorUiGood,
                    trackColor = ColorSlate.copy(alpha = 0.2f),
                )
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun AnalysisStatCard(label: String, value: String, modifier: Modifier) {
    PitwallFrame(modifier = modifier) {
        Column {
            Text(label, color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 9.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = ColorUiGood, fontFamily = ShareTechMonoFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TabSectionHeader(title: String) {
    Text(title, color = ColorSlate.copy(alpha = 0.7f), fontFamily = RajdhaniFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 2.sp)
}

@Composable
private fun LegendDot(color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(14.dp).height(2.dp).background(if (dashed) Color.Transparent else color)) {
            if (!dashed) {
                Box(Modifier.fillMaxSize().background(color))
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(label, color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 10.sp)
    }
}

private fun Modifier.tabIndicatorOffset(
    tabPosition: androidx.compose.material3.TabPosition,
): Modifier = this.then(
    Modifier.padding(start = tabPosition.left, end = tabPosition.right),
)
