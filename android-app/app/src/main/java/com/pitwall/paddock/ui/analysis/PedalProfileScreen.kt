package com.pitwall.paddock.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.data.PedalBehaviorResponse
import com.pitwall.paddock.ui.components.CrtOverlay
import com.pitwall.paddock.ui.components.KerbStripe
import com.pitwall.paddock.ui.components.PitwallFrame
import com.pitwall.paddock.ui.components.PitwallTopBar
import com.pitwall.paddock.ui.theme.*

@Composable
fun PedalProfileScreen(
    pedalBehavior: PedalBehaviorResponse?,
    isLoading: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorInk)
    ) {
        CrtOverlay(modifier = Modifier.fillMaxSize())

        Column(modifier = Modifier.fillMaxSize()) {
            PitwallTopBar(title = "PEDAL PROFILE")

            if (isLoading || pedalBehavior == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading pedal telemetry...", color = ColorSlate, fontFamily = RajdhaniFamily)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
                ) {
                    Text(
                        text = "SESSION FOOTWORK",
                        color = ColorSilver,
                        fontFamily = OrbitronFamily,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    
                    KerbStripe(modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))

                    val states = pedalBehavior.states
                    val throttle = states["throttle"]?.pct ?: 0f
                    val brake = states["brake"]?.pct ?: 0f
                    val coast = states["coast"]?.pct ?: 0f
                    val trail = states["trail"]?.pct ?: 0f

                    PitwallFrame(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Stacked Bar Chart
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp)
                            ) {
                                var currentX = 0f
                                
                                // Throttle (Green)
                                val wThrottle = size.width * throttle
                                drawRect(ColorBiosGreen, Offset(currentX, 0f), Size(wThrottle, size.height))
                                currentX += wThrottle

                                // Brake (Red)
                                val wBrake = size.width * brake
                                drawRect(ColorUiBad, Offset(currentX, 0f), Size(wBrake, size.height))
                                currentX += wBrake

                                // Trail (Orange)
                                val wTrail = size.width * trail
                                drawRect(ColorUiWarn, Offset(currentX, 0f), Size(wTrail, size.height))
                                currentX += wTrail

                                // Coast (Slate)
                                val wCoast = size.width * coast
                                drawRect(ColorSlate, Offset(currentX, 0f), Size(wCoast, size.height))
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Legend and Stats
                            PedalStatRow("ON THROTTLE", throttle, ColorBiosGreen)
                            Spacer(modifier = Modifier.height(12.dp))
                            PedalStatRow("HARD BRAKING", brake, ColorUiBad)
                            Spacer(modifier = Modifier.height(12.dp))
                            PedalStatRow("TRAIL BRAKING", trail, ColorUiWarn)
                            Spacer(modifier = Modifier.height(12.dp))
                            PedalStatRow("COASTING", coast, ColorSlate)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Simple Insights based on stats
                    val feedback = when {
                        coast > 0.15f -> "High coasting percentage. Transition directly from throttle to brake."
                        trail < 0.05f -> "Low trail braking. Bleed off the brakes slower into the apex."
                        throttle < 0.50f -> "Low throttle time. Ensure you're getting to full throttle sooner on corner exit."
                        else -> "Pedal overlap looks solid. Excellent footwork."
                    }
                    
                    PitwallFrame(accentColor = ColorUiInfo, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("COACH INSIGHT", color = ColorUiInfo, fontFamily = OrbitronFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(feedback, color = ColorSilver, fontFamily = RajdhaniFamily, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PedalStatRow(label: String, pct: Float, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).background(color))
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, color = ColorSilver, fontFamily = RajdhaniFamily, fontSize = 14.sp)
        }
        Text(
            text = "%.1f%%".format(pct * 100f),
            color = color,
            fontFamily = ShareTechMonoFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
