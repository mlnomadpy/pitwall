package com.pitwall.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.app.data.CoachingMessage
import com.pitwall.app.data.TelemetryFrame
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
    onReturnToTrack: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("LAPS", "SPEED", "CORNERS", "FRICTION", "PROFILE", "AI DEBRIEF")

    // Accumulate coaching for AI debrief
    val debriefMessages = remember { mutableStateListOf<String>() }
    LaunchedEffect(lastCoaching) {
        lastCoaching?.let {
            if (it.source == CoachingMessage.Source.WARM_PATH && it.text.isNotBlank()) {
                if (!debriefMessages.contains(it.text)) debriefMessages.add(0, it.text)
            }
        }
    }

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
                0 -> LapsTab()
                1 -> SpeedTraceTab(telemetry)
                2 -> CornersTab()
                3 -> FrictionTab(telemetry)
                4 -> ProfileTab()
                5 -> AiDebriefTab(debriefMessages)
            }
        }
    }
}

// ── LAPS ──────────────────────────────────────────────────────────────────────

@Composable
private fun LapsTab() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        // Stat cards
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("BEST", "1:39.6", Modifier.weight(1f))
            StatCard("vs AJ", "-1.2s", Modifier.weight(1f), PitwallColors.GripRed)
            StatCard("LAPS", "5", Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
        // Table header
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text("LAP", Modifier.weight(1f), color = PitwallColors.TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
            Text("TIME", Modifier.weight(1f), color = PitwallColors.TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
            Text("DELTA", Modifier.weight(1f), color = PitwallColors.TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
        }
        Divider(color = PitwallColors.Border, modifier = Modifier.padding(vertical = 8.dp))
        MOCK_LAPS.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp)) {
                Text("${row.lap}", Modifier.weight(1f), color = PitwallColors.TextPrimary, fontFamily = MonoFamily)
                val mins = (row.timeS / 60).toInt()
                val secs = String.format("%04.1f", row.timeS % 60)
                Text("$mins:$secs", Modifier.weight(1f), color = PitwallColors.TextPrimary, fontFamily = MonoFamily)
                val deltaColor = if (row.bestDeltaS < 0) PitwallColors.ThrottleGreen else PitwallColors.GripRed
                val sign = if (row.bestDeltaS >= 0) "+" else ""
                Text("$sign${row.bestDeltaS}", Modifier.weight(1f), color = deltaColor, fontFamily = MonoFamily)
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

// ── SPEED TRACE ───────────────────────────────────────────────────────────────

@Composable
private fun SpeedTraceTab(telemetry: TelemetryFrame?) {
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
            Box(
                Modifier.fillMaxSize().drawWithCache {
                    val maxSpeed = speedSnapshot.maxOrNull()?.coerceAtLeast(10f) ?: 200f
                    val path = Path()
                    speedSnapshot.forEachIndexed { i, v ->
                        val x = i / (speedSnapshot.size - 1f) * size.width
                        val y = size.height - (v / maxSpeed * size.height)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    onDrawBehind {
                        drawLine(PitwallColors.Border, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1f)
                        drawLine(PitwallColors.Border, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
                        drawPath(path, PitwallColors.SpeedBlue, style = Stroke(width = 2.dp.toPx()))
                    }
                }
            )
        }
    }
}

// ── CORNERS ───────────────────────────────────────────────────────────────────

@Composable
private fun CornersTab() {
    val corners = listOf(
        Triple("Turn 1",  0f,  -0.3f),
        Triple("Turn 3",  50f, -0.8f),
        Triple("Turn 6",  86f, +1.1f),
        Triple("Turn 9",  66f, -0.5f),
        Triple("Turn 10", 124f, +0.2f),
        Triple("Turn 11", 134f, -1.4f),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        corners.forEach { (name, elev, delta) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, Modifier.width(100.dp), color = PitwallColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(16.dp))
                Box(Modifier.weight(1f).height(6.dp).background(PitwallColors.Border, RoundedCornerShape(3.dp))) {
                    val fraction = ((delta + 2f) / 4f).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight()
                        .background(if (delta <= 0) PitwallColors.ThrottleGreen else PitwallColors.GripRed, RoundedCornerShape(3.dp)))
                }
                Spacer(Modifier.width(16.dp))
                val sign = if (delta >= 0) "+" else ""
                Text("$sign${delta}s", color = if (delta <= 0) PitwallColors.ThrottleGreen else PitwallColors.GripRed,
                    fontFamily = MonoFamily, fontSize = 13.sp, modifier = Modifier.width(56.dp))
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
        Box(Modifier.size(280.dp).drawWithCache {
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

// ── PROFILE ───────────────────────────────────────────────────────────────────

@Composable
private fun ProfileTab() {
    val skills = listOf(
        "Braking Precision" to 0.62f,
        "Trail Braking"     to 0.48f,
        "Corner Entry Speed" to 0.71f,
        "Throttle Pickup"   to 0.55f,
        "Consistency"       to 0.80f,
    )
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

// ── AI DEBRIEF ────────────────────────────────────────────────────────────────

@Composable
private fun AiDebriefTab(messages: List<String>) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (messages.isEmpty()) {
            Text("Coaching messages will appear here once the bridge delivers its first analysis.",
                color = PitwallColors.TextDim, fontSize = 13.sp)
        } else {
            messages.forEach { text ->
                Surface(color = PitwallColors.Surface, shape = RoundedCornerShape(8.dp)) {
                    Text(text, color = PitwallColors.TextPrimary, fontSize = 14.sp, lineHeight = 22.sp,
                        modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
