package com.pitwall.paddock.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.data.LapTimeTableResponse
import com.pitwall.paddock.ui.components.CrtOverlay
import com.pitwall.paddock.ui.components.PitwallFrame
import com.pitwall.paddock.ui.components.PitwallTopBar
import com.pitwall.paddock.ui.theme.*

@Composable
fun LapTimesScreen(
    lapTimeTable: LapTimeTableResponse?,
    isLoading: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorInk)
    ) {
        CrtOverlay(modifier = Modifier.fillMaxSize())

        Column(modifier = Modifier.fillMaxSize()) {
            PitwallTopBar(title = "LAP TIMES")

            if (isLoading || lapTimeTable == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading lap times...", color = ColorSlate, fontFamily = RajdhaniFamily)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header row
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("LAP", modifier = Modifier.width(48.dp), color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("TIME", modifier = Modifier.weight(1f), color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("DELTA", modifier = Modifier.weight(1f), color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    items(lapTimeTable.laps) { row ->
                        val isBest = row.isBest
                        val deltaColor = when {
                            isBest -> ColorBiosGreen
                            row.deltaToBestS > 0 -> ColorUiBad
                            else -> ColorSlate
                        }

                        PitwallFrame(
                            accentColor = if (isBest) ColorBiosGreen else ColorCharcoal,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = row.lapNumber.toString(),
                                    modifier = Modifier.width(48.dp),
                                    color = if (isBest) ColorBiosGreen else ColorSilver,
                                    fontFamily = OrbitronFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                val mins = (row.lapTimeS / 60).toInt()
                                val secs = String.format("%06.3f", row.lapTimeS % 60)
                                Text(
                                    text = "$mins:$secs",
                                    modifier = Modifier.weight(1f),
                                    color = if (isBest) ColorBiosGreen else ColorSilver,
                                    fontFamily = ShareTechMonoFamily,
                                    fontSize = 16.sp
                                )

                                val sign = if (row.deltaToBestS > 0) "+" else ""
                                Text(
                                    text = if (isBest) "-.---" else "$sign${String.format("%.3f", row.deltaToBestS)}",
                                    modifier = Modifier.weight(1f),
                                    color = deltaColor,
                                    fontFamily = ShareTechMonoFamily,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
