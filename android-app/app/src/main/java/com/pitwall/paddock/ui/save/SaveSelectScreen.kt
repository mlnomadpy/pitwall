package com.pitwall.paddock.ui.save

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.data.SaveSlot
import com.pitwall.paddock.ui.components.CrtOverlay
import com.pitwall.paddock.ui.components.PitwallFrame
import com.pitwall.paddock.ui.theme.ColorCharcoal
import com.pitwall.paddock.ui.theme.ColorInk
import com.pitwall.paddock.ui.theme.ColorUiGood
import com.pitwall.paddock.ui.theme.OrbitronFamily
import com.pitwall.paddock.ui.theme.RajdhaniFamily

@Composable
fun SaveSelectScreen(
    activeSlot: SaveSlot?,
    onSelectSave: (SaveSlot) -> Unit,
    onNewGame: () -> Unit
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
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "SELECT SAVE DATA",
                color = Color.White,
                fontFamily = OrbitronFamily,
                fontSize = 24.sp,
                letterSpacing = 4.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(0.9f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeSlot != null) {
                    PitwallFrame(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectSave(activeSlot) },
                        accentColor = ColorUiGood
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ColorCharcoal.copy(alpha = 0.5f))
                                .padding(24.dp)
                        ) {
                            Text(
                                text = activeSlot.driverName.uppercase(),
                                color = Color.White,
                                fontFamily = OrbitronFamily,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "LEVEL: ${activeSlot.driverLevel.uppercase()}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontFamily = RajdhaniFamily,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "COACH: ${activeSlot.preferredCoach.uppercase()}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontFamily = RajdhaniFamily,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(32.dp))
                }

                PitwallFrame(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNewGame() },
                    accentColor = Color(0xFF6E7686)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ColorCharcoal.copy(alpha = 0.3f))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NEW GAME",
                            color = Color.White.copy(alpha = 0.8f),
                            fontFamily = OrbitronFamily,
                            fontSize = 18.sp,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }
        }
    }
}
