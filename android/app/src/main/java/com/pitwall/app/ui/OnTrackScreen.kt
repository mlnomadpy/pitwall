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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.app.data.CoachingMessage
import com.pitwall.app.data.TelemetryFrame
import com.pitwall.app.ui.theme.MonoFamily
import com.pitwall.app.ui.theme.PitwallColors
import kotlinx.coroutines.delay

@Composable
fun OnTrackScreen(
    telemetry: TelemetryFrame?,
    lastCoaching: CoachingMessage?,
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

        // ── Two vertical bars ─────────────────────────────────────────────────
        Row(Modifier.fillMaxSize()) {
            // Green — grip available
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .padding(horizontal = 48.dp, vertical = 24.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GripBar(
                        fill = gripFill,
                        fillColor = if (nearLimit) PitwallColors.GripYellow else PitwallColors.GripGreen,
                        emptyColor = PitwallColors.GripGreenDim,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("GRIP", color = PitwallColors.GripGreen.copy(alpha = 0.6f),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                }
            }

            // Thin divider
            Box(Modifier.width(1.dp).fillMaxHeight().background(PitwallColors.Border))

            // Red — over limit
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .padding(horizontal = 48.dp, vertical = 24.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GripBar(
                        fill = limitFill,
                        fillColor = PitwallColors.GripRed,
                        emptyColor = PitwallColors.GripRedDim.copy(alpha = 0.3f),
                        fillFromTop = true,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("LIMIT",
                        color = if (limitFill > 0.05f) PitwallColors.GripRed else PitwallColors.TextDim,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                }
            }
        }

        // ── Status strip (top) ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
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
            Text("${telemetry?.speedKmh?.toInt() ?: 0} km/h",
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
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(48.dp),
        ) {
            Icon(Icons.Outlined.Home, "Back to menu", tint = PitwallColors.TextDim)
        }
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
