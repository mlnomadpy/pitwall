package com.pitwall.paddock.ui.stageclear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.data.SessionDetailResponse
import com.pitwall.paddock.ui.components.CrtOverlay
import com.pitwall.paddock.ui.components.CyberButton
import com.pitwall.paddock.ui.components.CyberButtonVariant
import com.pitwall.paddock.ui.components.PitwallFrame
import com.pitwall.paddock.ui.theme.*

@Composable
fun StageClearScreen(
    sessionDetail: SessionDetailResponse?,
    onBackToGarage: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorInk)
    ) {
        CrtOverlay(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "STAGE CLEAR",
                color = ColorUiGood,
                fontFamily = OrbitronFamily,
                fontWeight = FontWeight.Black,
                fontSize = 48.sp,
                letterSpacing = 4.sp,
                modifier = Modifier.padding(top = 32.dp, bottom = 48.dp)
            )

            if (sessionDetail == null) {
                Text("Analyzing session data...", color = ColorSlate, fontFamily = RajdhaniFamily)
            } else {
                // Medal Placeholder
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(ColorBiosGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "S",
                        color = ColorBiosGreen,
                        fontFamily = OrbitronFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 72.sp
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                PitwallFrame(modifier = Modifier.fillMaxWidth(0.8f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem("LAPS", sessionDetail.lapCount.toString())
                        val bestTime = sessionDetail.bestLapS
                        val bestTimeStr = if (bestTime != null && bestTime > 0) {
                            val mins = (bestTime / 60).toInt()
                            val secs = String.format("%06.3f", bestTime % 60)
                            "$mins:$secs"
                        } else {
                            "--:--"
                        }
                        StatItem("BEST TIME", bestTimeStr)
                        StatItem("GRADE", "S")
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = "COACH NOTES",
                    color = ColorSlate,
                    fontFamily = OrbitronFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (sessionDetail.notes.isEmpty()) {
                    Text("No significant notes for this session.", color = ColorSlate, fontFamily = RajdhaniFamily)
                } else {
                    sessionDetail.notes.take(3).forEach { note ->
                        PitwallFrame(accentColor = ColorUiInfo, modifier = Modifier.fillMaxWidth(0.8f).padding(bottom = 8.dp)) {
                            Text(
                                text = note.text,
                                color = ColorSilver,
                                fontFamily = RajdhaniFamily,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(64.dp))

            CyberButton(
                text = "BACK TO GARAGE",
                onClick = onBackToGarage,
                variant = CyberButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth(0.8f).height(64.dp)
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 12.sp, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = ColorUiGood, fontFamily = ShareTechMonoFamily, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}
