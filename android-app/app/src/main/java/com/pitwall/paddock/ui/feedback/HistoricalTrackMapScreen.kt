package com.pitwall.paddock.ui.feedback

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.data.CornerMarker
import com.pitwall.paddock.data.TrackOutline
import com.pitwall.paddock.data.TrackPoint
import com.pitwall.paddock.ui.components.CrtOverlay
import com.pitwall.paddock.ui.components.KerbStripe
import com.pitwall.paddock.ui.components.PitwallFrame
import com.pitwall.paddock.ui.components.PitwallTopBar
import com.pitwall.paddock.ui.components.StatusPip
import com.pitwall.paddock.ui.theme.ColorBiosGreen
import com.pitwall.paddock.ui.theme.ColorCharcoal
import com.pitwall.paddock.ui.theme.ColorInk
import com.pitwall.paddock.ui.theme.ColorSlate
import com.pitwall.paddock.ui.theme.ColorSilver
import com.pitwall.paddock.ui.theme.ColorUiGood
import com.pitwall.paddock.ui.theme.ColorUiInfo
import com.pitwall.paddock.ui.theme.Dimens
import com.pitwall.paddock.ui.theme.OrbitronFamily
import com.pitwall.paddock.ui.theme.RajdhaniFamily
import com.pitwall.paddock.ui.theme.ShareTechMonoFamily

@Composable
fun HistoricalTrackMapScreen(
    trackOutline: TrackOutline?,
    currentLap: Int = 0,
    currentDistanceM: Float = 0f,
    bestLapTimeStr: String = "--:--",
    useMph: Boolean = false,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(ColorInk),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            PitwallTopBar(title = "TRACK MAP")
            KerbStripe()

            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(Dimens.SpaceSm))

                // ── Header row ────────────────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text          = "SONOMA RACEWAY",
                            color         = ColorSilver,
                            fontFamily    = OrbitronFamily,
                            fontSize      = 16.sp,
                            fontWeight    = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            StatusPip(active = true, color = ColorBiosGreen)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text          = "LAP $currentLap  ·  3.765 km",
                                color         = ColorSlate,
                                fontFamily    = RajdhaniFamily,
                                fontSize      = 10.sp,
                                letterSpacing = 0.8.sp,
                            )
                        }
                    }
                    PitwallFrame(accentColor = ColorUiGood) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("BEST LAP", color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 8.sp, letterSpacing = 1.sp)
                            Text(bestLapTimeStr, color = ColorUiGood, fontFamily = ShareTechMonoFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                }

                Spacer(Modifier.height(Dimens.SpaceMd))

                // ── Mini-map ──────────────────────────────────────────────────
                Surface(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    color    = ColorCharcoal.copy(alpha = 0.88f),
                    shape    = RoundedCornerShape(Dimens.CardCornerLg),
                    border   = BorderStroke(Dimens.BorderNormal, ColorSlate.copy(alpha = 0.3f)),
                ) {
                    if (trackOutline != null) {
                        MiniMapCanvas(
                            outline     = trackOutline,
                            distanceM   = currentDistanceM,
                            modifier    = Modifier.fillMaxSize().padding(12.dp),
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Loading track…", color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(Dimens.SpaceMd))

                // ── Corner marker legend ──────────────────────────────────────
                Text("APEX MARKERS", color = ColorSlate.copy(alpha = 0.7f), fontFamily = RajdhaniFamily, fontSize = 9.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    LegendItem(ColorUiGood,  "Track line")
                    LegendItem(ColorUiInfo,  "Apex marker")
                    LegendItem(ColorBiosGreen, "Driver position")
                }

                Spacer(Modifier.height(Dimens.SpaceMd))

                // ── Sector info ───────────────────────────────────────────────
                trackOutline?.let { outline ->
                    PitwallFrame {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly) {
                            listOf("S1", "S2", "S3").forEachIndexed { i, name ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(name, color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 9.sp, letterSpacing = 1.sp)
                                    Text("--.-s", color = ColorSilver, fontFamily = ShareTechMonoFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        text          = listOf("0 – 1255m", "1255 – 2510m", "2510 – 3765m")[i],
                                        color         = ColorSlate.copy(alpha = 0.6f),
                                        fontFamily    = RajdhaniFamily,
                                        fontSize      = 8.sp,
                                        letterSpacing = 0.5.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Dimens.SpaceXl))
            }
        }

        CrtOverlay()
    }
}

// ── Mini-map canvas — ported from android/ui/OnTrackScreen.kt ─────────────────

@Composable
private fun MiniMapCanvas(
    outline: TrackOutline,
    distanceM: Float,
    modifier: Modifier = Modifier,
) {
    val pulse   = rememberInfiniteTransition(label = "minimap_pulse")
    val pRadius by pulse.animateFloat(
        initialValue = 5f, targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse_r",
    )
    val pAlpha by pulse.animateFloat(
        initialValue = 1f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse_a",
    )

    val driverPoint = outline.distanceToPoint(distanceM)

    Box(
        modifier
            .drawBehind {
                val pts = outline.points
                if (pts.size < 2) return@drawBehind

                // ── Track outline ──────────────────────────────────────────────
                val path = Path()
                pts.forEachIndexed { i, p ->
                    val px = p.x * size.width; val py = p.y * size.height
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.lineTo(pts.first().x * size.width, pts.first().y * size.height)
                drawPath(path, ColorUiGood, style = Stroke(width = 2.5.dp.toPx()))

                // ── Corner apex dots ──────────────────────────────────────────
                outline.cornerMarkers.forEach { marker ->
                    drawCircle(
                        color  = ColorUiInfo.copy(alpha = 0.85f),
                        radius = 4.dp.toPx(),
                        center = Offset(marker.posX * size.width, marker.posY * size.height),
                    )
                }

                // ── Driver position dot (pulsing) ─────────────────────────────
                val dx = driverPoint.x * size.width
                val dy = driverPoint.y * size.height
                drawCircle(ColorBiosGreen.copy(alpha = pAlpha * 0.25f), pRadius * 2.2f, Offset(dx, dy))
                drawCircle(ColorBiosGreen.copy(alpha = pAlpha),         pRadius,        Offset(dx, dy))
            },
    )
}

// ── Grip bar — ported from android/ui/OnTrackScreen.kt ───────────────────────

@Composable
fun GripBar(
    fill: Float,
    fillColor: Color,
    emptyColor: Color,
    modifier: Modifier = Modifier,
    fillFromTop: Boolean = false,
) {
    Box(
        modifier.drawBehind {
            val radius = 8.dp.toPx()
            drawRoundRect(color = emptyColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius))
            val fillH = size.height * fill.coerceIn(0f, 1f)
            val top   = if (fillFromTop) 0f else size.height - fillH
            drawRoundRect(
                color        = fillColor,
                topLeft      = Offset(0f, top),
                size         = androidx.compose.ui.geometry.Size(size.width, fillH.coerceAtLeast(0f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
            )
        },
    )
}

// ── Legend ────────────────────────────────────────────────────────────────────

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(1.dp)))
        Spacer(Modifier.width(4.dp))
        Text(label, color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 9.sp)
    }
}
