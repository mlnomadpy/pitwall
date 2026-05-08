package com.pitwall.paddock.ui.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.ui.components.CrtOverlay
import com.pitwall.paddock.ui.components.CyberButton
import com.pitwall.paddock.ui.components.CyberButtonVariant
import com.pitwall.paddock.ui.components.KerbStripe
import com.pitwall.paddock.ui.components.PitwallFrame
import com.pitwall.paddock.ui.components.PitwallTopBar
import com.pitwall.paddock.ui.components.StatusPip
import com.pitwall.paddock.ui.theme.ColorBiosGreen
import com.pitwall.paddock.ui.theme.ColorCharcoal
import com.pitwall.paddock.ui.theme.ColorInk
import com.pitwall.paddock.ui.theme.ColorSlate
import com.pitwall.paddock.ui.theme.ColorSilver
import com.pitwall.paddock.ui.theme.ColorUiBad
import com.pitwall.paddock.ui.theme.ColorUiGood
import com.pitwall.paddock.ui.theme.ColorUiInfo
import com.pitwall.paddock.ui.theme.Dimens
import com.pitwall.paddock.ui.theme.OrbitronFamily
import com.pitwall.paddock.ui.theme.RajdhaniFamily
import com.pitwall.paddock.ui.theme.ShareTechMonoFamily

@Composable
fun GarageScreen(
    bridgeLine: String,
    apiBaseUrl: String,
    isBridgeOnline: Boolean,
    onRefreshBridge: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenHistoricalMap: () -> Unit,
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
            PitwallTopBar()

            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(Dimens.SpaceMd))

                // ── Page heading ──────────────────────────────────────────────
                Text(
                    text          = "GARAGE",
                    color         = ColorSilver,
                    fontFamily    = OrbitronFamily,
                    fontWeight    = FontWeight.Black,
                    fontSize      = 28.sp,
                    letterSpacing = 2.sp,
                )

                KerbStripe(modifier = Modifier.padding(top = 10.dp, bottom = 20.dp))

                // ── Bridge status ─────────────────────────────────────────────
                SectionLabel("PITWALL BRIDGE")
                Spacer(Modifier.height(8.dp))

                PitwallFrame(accentColor = if (isBridgeOnline) ColorBiosGreen else ColorUiBad) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusPip(active = isBridgeOnline, color = if (isBridgeOnline) ColorBiosGreen else ColorUiBad)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text       = if (isBridgeOnline) "ONLINE" else "OFFLINE",
                                    color      = if (isBridgeOnline) ColorBiosGreen else ColorUiBad,
                                    fontFamily = OrbitronFamily,
                                    fontSize   = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                text       = bridgeLine,
                                color      = ColorSlate,
                                fontFamily = ShareTechMonoFamily,
                                fontSize   = 11.sp,
                                modifier   = Modifier.padding(top = 6.dp),
                            )
                        }
                        IconButton(onClick = onRefreshBridge, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = ColorUiGood)
                        }
                    }
                }

                Spacer(Modifier.height(Dimens.SpaceMd))

                // ── API endpoint display ──────────────────────────────────────
                SectionLabel("ENDPOINT")
                Spacer(Modifier.height(8.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(ColorInk, RoundedCornerShape(Dimens.CardCornerSm))
                        .border(Dimens.BorderNormal, ColorSlate.copy(alpha = 0.2f), RoundedCornerShape(Dimens.CardCornerSm))
                        .padding(12.dp),
                ) {
                    Text(
                        text       = apiBaseUrl.ifEmpty { "http://10.0.2.2:8765" },
                        color      = ColorUiGood,
                        fontFamily = ShareTechMonoFamily,
                        fontSize   = 12.sp,
                    )
                }

                Spacer(Modifier.height(Dimens.SpaceXl))

                // ── Quick nav to analysis modules ─────────────────────────────
                SectionLabel("POST-SESSION TOOLS")
                Spacer(Modifier.height(8.dp))

                GarageNavCard(
                    icon    = Icons.Filled.Analytics,
                    title   = "ANALYSIS HUB",
                    subtitle = "6 tabs — Laps, Insights, Speed, Corners, Friction, Profile",
                    tint    = ColorUiGood,
                    onClick = onOpenAnalysis,
                )

                Spacer(Modifier.height(8.dp))

                GarageNavCard(
                    icon     = Icons.Filled.Route,
                    title    = "HISTORICAL TRACK MAP",
                    subtitle = "GPS mini-map · 1065-point Sonoma reference line",
                    tint     = ColorUiInfo,
                    onClick  = onOpenHistoricalMap,
                )

                Spacer(Modifier.height(Dimens.SpaceXl))
            }
        }

        CrtOverlay()
    }
}

// ── Supporting composables ────────────────────────────────────────────────────

@Composable
private fun SectionLabel(label: String) {
    Text(
        text          = label,
        color         = ColorSlate.copy(alpha = 0.7f),
        fontFamily    = RajdhaniFamily,
        fontSize      = 10.sp,
        fontWeight    = FontWeight.SemiBold,
        letterSpacing = 2.sp,
    )
}

@Composable
private fun GarageNavCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    PitwallFrame(accentColor = tint) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = ColorSilver, fontFamily = OrbitronFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                CyberButton("OPEN", onClick = onClick, variant = CyberButtonVariant.Outlined, accentColor = tint)
            }
            Text(subtitle, color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
