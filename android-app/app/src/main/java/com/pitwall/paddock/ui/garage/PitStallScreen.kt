package com.pitwall.paddock.ui.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.data.SaveSlot
import com.pitwall.paddock.ui.components.*
import com.pitwall.paddock.ui.theme.*

@Composable
fun PitStallScreen(
    bridgeLine: String,
    isBridgeOnline: Boolean,
    activeSlot: SaveSlot?,
    onRefreshBridge: () -> Unit,
    onStartSession: (String, String, String, String) -> Unit,
) {
    var trackName by remember { mutableStateOf("Sonoma Raceway") }
    var carName by remember { mutableStateOf("Porsche 911 GT3") }

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
            PitwallTopBar()

            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(Dimens.SpaceMd))

                Text(
                    text = "PIT STALL",
                    color = ColorSilver,
                    fontFamily = OrbitronFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp,
                    letterSpacing = 2.sp,
                )

                KerbStripe(modifier = Modifier.padding(top = 10.dp, bottom = 20.dp))

                // ── Bridge & Telemetry Status ───────────────────────────────
                SectionLabel("HARDWARE TELEMETRY")
                Spacer(Modifier.height(8.dp))

                PitwallFrame(accentColor = if (isBridgeOnline) ColorBiosGreen else ColorUiBad) {
                    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StatusPip(active = isBridgeOnline, color = if (isBridgeOnline) ColorBiosGreen else ColorUiBad)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (isBridgeOnline) "BRIDGE ONLINE" else "BRIDGE OFFLINE",
                                        color = if (isBridgeOnline) ColorBiosGreen else ColorUiBad,
                                        fontFamily = OrbitronFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Text(
                                    text = bridgeLine,
                                    color = ColorSlate,
                                    fontFamily = ShareTechMonoFamily,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            IconButton(onClick = onRefreshBridge, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = ColorUiGood)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Connection Rows
                ConnRow(
                    icon = Icons.Default.Speed,
                    name = "Racelogic VBOX Sport",
                    status = if (isBridgeOnline) "CONNECTED (20Hz)" else "SEARCHING...",
                    isActive = isBridgeOnline
                )
                Spacer(Modifier.height(8.dp))
                ConnRow(
                    icon = Icons.Default.DirectionsCar,
                    name = "OBDLink MX+",
                    status = if (isBridgeOnline) "CONNECTED (100Hz CAN)" else "SEARCHING...",
                    isActive = isBridgeOnline
                )
                Spacer(Modifier.height(8.dp))
                ConnRow(
                    icon = Icons.Default.Headset,
                    name = "Coach Audio",
                    status = "CONNECTED (A2DP)",
                    isActive = true
                )

                Spacer(Modifier.height(32.dp))

                // ── Session Config ──────────────────────────────────────────
                SectionLabel("SESSION CONFIG")
                Spacer(Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ConfigField(
                        label = "DRIVER",
                        value = activeSlot?.driverName?.uppercase() ?: "UNKNOWN",
                        modifier = Modifier.weight(1f),
                        readOnly = true
                    )
                    ConfigField(
                        label = "LEVEL",
                        value = activeSlot?.driverLevel?.uppercase() ?: "UNKNOWN",
                        modifier = Modifier.weight(1f),
                        readOnly = true
                    )
                }
                
                Spacer(Modifier.height(16.dp))

                ConfigField(
                    label = "TRACK",
                    value = trackName,
                    onValueChange = { trackName = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                ConfigField(
                    label = "CAR",
                    value = carName,
                    onValueChange = { carName = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(32.dp))

                // ── Start Button ────────────────────────────────────────────
                CyberButton(
                    text = "START SESSION",
                    onClick = {
                        onStartSession(
                            activeSlot?.driverName ?: "UNKNOWN",
                            activeSlot?.driverLevel ?: "UNKNOWN",
                            trackName,
                            carName
                        )
                    },
                    variant = CyberButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                )

                Spacer(Modifier.height(48.dp))
            }
        }

        CrtOverlay()
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label,
        color = ColorSlate.copy(alpha = 0.7f),
        fontFamily = RajdhaniFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
    )
}

@Composable
private fun ConnRow(
    icon: ImageVector,
    name: String,
    status: String,
    isActive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorCharcoal.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) ColorUiGood else ColorSlate,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = ColorSilver,
                fontFamily = OrbitronFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = status,
                color = if (isActive) ColorUiGood else ColorSlate,
                fontFamily = RajdhaniFamily,
                fontSize = 12.sp
            )
        }
        StatusPip(active = isActive, color = ColorUiGood)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit = {},
    readOnly: Boolean = false
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = ColorSlate,
            fontFamily = OrbitronFamily,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = if (readOnly) ColorSlate else Color.White,
                fontFamily = RajdhaniFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ColorUiGood,
                unfocusedBorderColor = ColorSlate.copy(alpha = 0.5f),
                cursorColor = ColorUiGood,
                focusedContainerColor = ColorCharcoal.copy(alpha = 0.3f),
                unfocusedContainerColor = ColorCharcoal.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
