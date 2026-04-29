package com.pitwall.app.ui

import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.app.data.CoachingMessage
import com.pitwall.app.data.TelemetryFrame
import com.pitwall.app.data.TrackOutline
import com.pitwall.app.ui.theme.MonoFamily
import com.pitwall.app.ui.theme.PitwallColors
import kotlinx.coroutines.delay

@Composable
fun OnTrackScreen(
    telemetry: TelemetryFrame?,
    lastCoaching: CoachingMessage?,
    trackOutline: TrackOutline?,
    useMph: Boolean,
    onEnterPaddock: () -> Unit,
    onReturnToSetup: () -> Unit,
) {
    val gripUsage = telemetry?.gripUsage ?: 0f
    val nearLimit = gripUsage > 0.80f

    val gripFill by animateFloatAsState(
        targetValue = (1f - gripUsage).coerceIn(0f, 1f),
        animationSpec = tween(80, easing = LinearOutSlowInEasing),
        label = "grip",
    )
    val limitFill by animateFloatAsState(
        targetValue = ((gripUsage - 0.95f) / 0.3f).coerceIn(0f, 1f),
        animationSpec = tween(80, easing = LinearOutSlowInEasing),
        label = "limit",
    )

    // Coaching toast visibility
    var showToast by remember { mutableStateOf(false) }
    LaunchedEffect(lastCoaching) {
        if (lastCoaching != null) {
            showToast = true
            delay(4_000)
            showToast = false
        }
    }

    // Confirm dialog
    var showExitDialog by remember { mutableStateOf(false) }
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = PitwallColors.Surface,
            title = { Text("End session?", color = PitwallColors.TextPrimary) },
            text = { Text("Return to the main menu and stop recording.", color = PitwallColors.TextDim) },
            confirmButton = {
                TextButton(onClick = { showExitDialog = false; onReturnToSetup() }) {
                    Text("END SESSION", color = PitwallColors.GripRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("KEEP DRIVING", color = PitwallColors.GripGreen)
                }
            },
        )
    }

    Box(Modifier.fillMaxSize().background(PitwallColors.Background)) {

        // ── Mini-map (Main Background) ─────────────────────────────────────────
        if (trackOutline != null) {
            MiniMapOverlay(
                outline = trackOutline,
                distanceM = telemetry?.distance?.value ?: 0f,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 56.dp, bottom = 80.dp, start = 20.dp, end = 20.dp)
            )
        }

        // ── Two vertical bars (Small, Bottom Right) ───────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 32.dp)
                .height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Green — grip available
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GripBar(
                    fill = gripFill,
                    fillColor = if (nearLimit) PitwallColors.GripYellow else PitwallColors.GripGreen,
                    emptyColor = PitwallColors.GripGreenDim,
                    modifier = Modifier.width(24.dp).weight(1f),
                )
                Spacer(Modifier.height(8.dp))
                Text("GRIP", color = PitwallColors.GripGreen.copy(alpha = 0.6f),
                    fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }

            // Red — over limit
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GripBar(
                    fill = limitFill,
                    fillColor = PitwallColors.GripRed,
                    emptyColor = PitwallColors.GripRedDim.copy(alpha = 0.3f),
                    fillFromTop = true,
                    modifier = Modifier.width(24.dp).weight(1f),
                )
                Spacer(Modifier.height(8.dp))
                Text("LIMIT",
                    color = if (limitFill > 0.05f) PitwallColors.GripRed else PitwallColors.TextDim,
                    fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }

        // ── Status strip (top) ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiveDot()
            Spacer(Modifier.weight(1f))
            Text("LAP ${telemetry?.lap ?: 0}",
                color = PitwallColors.TextDim, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
            Spacer(Modifier.width(24.dp))
            LapTimer(telemetry?.lapTime ?: 0f)
            Spacer(Modifier.weight(1f))
            val speedDisplay = if (useMph) telemetry?.speedMph?.toInt() ?: 0 else telemetry?.speedKmh?.toInt() ?: 0
            val speedUnit = if (useMph) "mph" else "km/h"
            Text("$speedDisplay $speedUnit",
                color = PitwallColors.TextDim, fontSize = 12.sp, fontFamily = MonoFamily)
        }

        // ── Corner indicator (bottom centre) ─────────────────────────────────
        telemetry?.let { frame ->
            if (frame.inCorner || frame.cornerProximity < 200f) {
                val label = frame.currentCorner ?: "${frame.cornerProximity.toInt()}m"
                val color = if (frame.inCorner) PitwallColors.GripYellow else PitwallColors.TextDim
                Text(
                    label.uppercase(),
                    color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                )
            }
        }

        // ── Coaching toast ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showToast && lastCoaching != null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 40.dp, vertical = 80.dp),
        ) {
            lastCoaching?.let { CoachingToast(it) }
        }

        // ── Back to menu ──────────────────────────────────────────────────────
        IconButton(
            onClick = { showExitDialog = true },
            modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 4.dp).size(48.dp),
        ) {
            Icon(Icons.Outlined.Home, "Back to menu", tint = PitwallColors.TextDim)
        }

        // ── Paddock Button (Top Right) ────────────────────────────────────────
        TextButton(
            onClick = onEnterPaddock,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 12.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = PitwallColors.GripYellow)
        ) {
            Text("PADDOCK", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

// ── Mini-map overlay ──────────────────────────────────────────────────────────

@Composable
private fun MiniMapOverlay(
    outline: TrackOutline,
    distanceM: Float,
    modifier: Modifier = Modifier,
) {
    // Pulsing animation for driver dot
    val pulse = rememberInfiniteTransition(label = "minimap_pulse")
    val pulseRadius by pulse.animateFloat(
        initialValue = 5f, targetValue = 9f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_r",
    )
    val pulseAlpha by pulse.animateFloat(
        initialValue = 1f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_a",
    )

    val driverPoint = outline.distanceToPoint(distanceM)

    Surface(
        modifier = modifier,
        color = PitwallColors.Surface.copy(alpha = 0.88f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, PitwallColors.Border),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(8.dp)
                .drawBehind {
                    val pts = outline.points
                    if (pts.size < 2) return@drawBehind

                    // ── Draw track outline ──────────────────────────────────
                    val path = Path()
                    pts.forEachIndexed { i, p ->
                        val px = p.x * size.width
                        val py = p.y * size.height
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    // Close the loop
                    path.lineTo(pts.first().x * size.width, pts.first().y * size.height)
                    drawPath(path, PitwallColors.Border, style = Stroke(width = 2.5f))

                    // ── Corner apex dots ────────────────────────────────────
                    outline.cornerMarkers.forEach { marker ->
                        drawCircle(
                            color = PitwallColors.GripGreen.copy(alpha = 0.7f),
                            radius = 3f,
                            center = Offset(marker.posX * size.width, marker.posY * size.height),
                        )
                    }

                    // ── Driver position dot (pulsing) ───────────────────────
                    val dx = driverPoint.x * size.width
                    val dy = driverPoint.y * size.height
                    // Outer glow
                    drawCircle(
                        color = PitwallColors.GripGreen.copy(alpha = pulseAlpha * 0.3f),
                        radius = pulseRadius * 1.8f,
                        center = Offset(dx, dy),
                    )
                    // Inner dot
                    drawCircle(
                        color = PitwallColors.GripGreen.copy(alpha = pulseAlpha),
                        radius = pulseRadius,
                        center = Offset(dx, dy),
                    )
                }
        )
    }
}

// ── Vertical bar drawn with Canvas ───────────────────────────────────────────

@Composable
private fun GripBar(
    fill: Float,
    fillColor: Color,
    emptyColor: Color,
    modifier: Modifier = Modifier,
    fillFromTop: Boolean = false,
) {
    Box(modifier = modifier.drawBehind {
        val radius = 8.dp.toPx()
        // Empty background (rounded)
        drawRoundRect(
            color = emptyColor,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius)
        )
        // Filled portion
        val fillH = size.height * fill.coerceIn(0f, 1f)
        val top = if (fillFromTop) 0f else size.height - fillH
        drawRoundRect(
            color = fillColor,
            topLeft = androidx.compose.ui.geometry.Offset(0f, top),
            size = androidx.compose.ui.geometry.Size(size.width, fillH.coerceAtLeast(0f)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
        )
    }) {}
}

// ── Supporting composables ────────────────────────────────────────────────────

@Composable
private fun LiveDot() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(PitwallColors.GripGreen, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(6.dp))
        Text("LIVE", color = PitwallColors.GripGreen, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    }
}

@Composable
private fun LapTimer(seconds: Float) {
    val mins = (seconds / 60).toInt()
    val secs = (seconds % 60).let { String.format("%04.1f", it) }
    Text("$mins:$secs", color = PitwallColors.TextDim, fontSize = 12.sp, fontFamily = MonoFamily, letterSpacing = 1.sp)
}

@Composable
private fun CoachingToast(event: CoachingMessage) {
    val borderColor = when (event.priority) {
        3 -> PitwallColors.GripRed
        2 -> PitwallColors.GripYellow
        else -> PitwallColors.SpeedBlue
    }
    Surface(
        color = PitwallColors.Surface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            event.text, color = PitwallColors.TextPrimary,
            fontSize = 15.sp, lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}
