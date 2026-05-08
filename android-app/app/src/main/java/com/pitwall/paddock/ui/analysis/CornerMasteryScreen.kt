package com.pitwall.paddock.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.data.ScorecardResponse
import com.pitwall.paddock.ui.components.CrtOverlay
import com.pitwall.paddock.ui.components.PitwallFrame
import com.pitwall.paddock.ui.components.PitwallTopBar
import com.pitwall.paddock.ui.theme.*

@Composable
fun CornerMasteryScreen(
    scorecard: ScorecardResponse?,
    isLoading: Boolean,
    useMph: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorInk)
    ) {
        CrtOverlay(modifier = Modifier.fillMaxSize())

        Column(modifier = Modifier.fillMaxSize()) {
            PitwallTopBar(title = "CORNER MASTERY")

            if (isLoading || scorecard == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading corner analysis...", color = ColorSlate, fontFamily = RajdhaniFamily)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(scorecard.scorecard.entries.toList()) { (cornerId, grade) ->
                        val overallGrade = grade.exitGrade // simplified overall grade
                        val accentColor = when (overallGrade) {
                            "A" -> ColorBiosGreen
                            "B" -> ColorUiGood
                            "C" -> ColorUiWarn
                            else -> ColorUiBad
                        }

                        PitwallFrame(
                            accentColor = accentColor,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = cornerId.uppercase(),
                                        color = ColorSilver,
                                        fontFamily = OrbitronFamily,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(accentColor.copy(alpha = 0.2f))
                                            .padding(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "GRADE $overallGrade",
                                            color = accentColor,
                                            fontFamily = OrbitronFamily,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    GradeMetric("IN", grade.entryGrade)
                                    GradeMetric("MID", grade.apexGrade)
                                    GradeMetric("OUT", grade.exitGrade)
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                val speedDelta = if (useMph) grade.avgSpeedDeltaKmh * 0.621371f else grade.avgSpeedDeltaKmh
                                val speedUnit = if (useMph) "MPH" else "KM/H"
                                val sign = if (speedDelta > 0) "+" else ""
                                val deltaColor = if (speedDelta >= 0) ColorBiosGreen else ColorUiBad
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("SPEED DELTA", color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 10.sp)
                                        Text("$sign${String.format("%.1f", speedDelta)} $speedUnit", color = deltaColor, fontFamily = ShareTechMonoFamily, fontSize = 16.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("PEAK G", color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 10.sp)
                                        Text(String.format("%.2f", grade.maxG), color = ColorUiWarn, fontFamily = ShareTechMonoFamily, fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeMetric(label: String, grade: String) {
    val color = when (grade) {
        "A" -> ColorBiosGreen
        "B" -> ColorUiGood
        "C" -> ColorUiWarn
        else -> ColorUiBad
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 10.sp)
        Text(grade, color = color, fontFamily = OrbitronFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}
