package com.pitwall.paddock.ui.ontrack

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.data.CueEvent
import com.pitwall.paddock.data.TelemetryFrame
import com.pitwall.paddock.data.TrackOutline
import com.pitwall.paddock.ui.components.CrtOverlay
import com.pitwall.paddock.ui.components.KerbStripe
import com.pitwall.paddock.ui.components.PitwallFrame
import com.pitwall.paddock.ui.components.StatusPip
import com.pitwall.paddock.ui.theme.*
import kotlin.math.min

@Composable
fun OnTrackHudScreen(
    sessionId: String,
    telemetryFrame: TelemetryFrame?,
    activeCue: CueEvent?,
    trackOutline: TrackOutline?,
    useMph: Boolean,
    onOpenStreams: (String) -> Unit,
    onCloseStreams: () -> Unit,
    onEndSession: () -> Unit
) {
    val context = LocalContext.current
    
    // Lock to landscape
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        
        onOpenStreams(sessionId)
        
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            onCloseStreams()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorInk)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Left Column (weight 1) ──────────────────────────────────────────
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                PitwallFrame(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "LAP POSITION",
                            color = ColorSlate,
                            fontFamily = OrbitronFamily,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "LAP ${telemetryFrame?.lapNumber ?: 1}",
                            color = ColorUiGood,
                            fontFamily = RajdhaniFamily,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    SonomaMiniMap(
                        outline = trackOutline,
                        distanceM = telemetryFrame?.distance ?: 0f,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                PitwallFrame(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "SONOMA GP",
                            color = ColorSilver,
                            fontFamily = OrbitronFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }

            // ── Center Column (weight 1.2) ──────────────────────────────────────
            Column(
                modifier = Modifier.weight(1.2f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val speed = telemetryFrame?.speed ?: 0f
                val displaySpeed = if (useMph) speed * 2.237f else speed * 3.6f
                val unitLabel = if (useMph) "MPH" else "KM/H"
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "%.0f".format(displaySpeed),
                        color = Color.White,
                        fontFamily = RajdhaniFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 140.sp,
                        lineHeight = 140.sp
                    )
                    Text(
                        text = unitLabel,
                        color = ColorSlate,
                        fontFamily = OrbitronFamily,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val frictionPct = min(100f, ((telemetryFrame?.comboG ?: 0f) / 1.3f) * 100f)
                    val slipPct = if (frictionPct > 90f) (frictionPct - 90f) * 10f else 0f
                    
                    GripBar(pct = frictionPct, label = "GRIP", modifier = Modifier.padding(end = 32.dp))
                    GripBar(pct = slipPct, label = "SLIP")
                }
            }

            // ── Right Column (weight 1) ─────────────────────────────────────────
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                PitwallFrame(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "LAST LAP / DELTA",
                            color = ColorSlate,
                            fontFamily = OrbitronFamily,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "1:42.531", // Placeholders for now until hooked to lap time table
                            color = ColorUiGood,
                            fontFamily = RajdhaniFamily,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "-0.12s",
                            color = ColorUiGood,
                            fontFamily = ShareTechMonoFamily,
                            fontSize = 16.sp
                        )
                    }
                }

                PitwallFrame(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = ColorSlate
                ) {
                    Column(
                        modifier = Modifier.background(ColorCharcoal.copy(alpha = 0.5f)).padding(16.dp)
                    ) {
                        Text("TELEMETRY", color = ColorSilver, fontFamily = OrbitronFamily, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("THROTTLE: %.0f%%".format(telemetryFrame?.throttlePct ?: 0f), color = ColorUiGood, fontFamily = ShareTechMonoFamily, fontSize = 12.sp)
                        Text("BRAKE: %.0f%%".format((telemetryFrame?.brakeBar ?: 0f) * 100f), color = ColorUiBad, fontFamily = ShareTechMonoFamily, fontSize = 12.sp)
                        Text("LAT G: %.2f".format(telemetryFrame?.lateralG ?: 0f), color = ColorUiWarn, fontFamily = ShareTechMonoFamily, fontSize = 12.sp)
                    }
                }

                PitwallFrame(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val isLive = telemetryFrame != null
                        StatusPip(active = isLive, color = if (isLive) ColorBiosGreen else ColorUiBad)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isLive) "LIVE STREAM" else "NO SIGNAL",
                            color = if (isLive) ColorBiosGreen else ColorUiBad,
                            fontFamily = OrbitronFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Overlays
        CueBand(
            cue = activeCue,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
        )
        
        CrtOverlay()
    }
}
