package com.pitwall.paddock.ui.briefing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.data.BriefResponse
import com.pitwall.paddock.ui.components.CrtOverlay
import com.pitwall.paddock.ui.components.CyberButton
import com.pitwall.paddock.ui.components.CyberButtonVariant
import com.pitwall.paddock.ui.components.KerbStripe
import com.pitwall.paddock.ui.components.PitwallFrame
import com.pitwall.paddock.ui.components.PitwallTopBar
import com.pitwall.paddock.ui.theme.*

@Composable
fun BriefingScreen(
    briefResponse: BriefResponse?,
    isLoading: Boolean,
    onFetchBrief: () -> Unit,
    onCommence: () -> Unit,
    onOpenWebBrief: () -> Unit,
) {
    LaunchedEffect(Unit) {
        if (briefResponse == null && !isLoading) {
            onFetchBrief()
        }
    }

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

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ColorUiGood)
                }
            } else if (briefResponse != null) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(Dimens.SpaceMd))

                    // ── Header ────────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SESSION PREP",
                                color = ColorUiInfo,
                                fontFamily = RajdhaniFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 2.sp,
                            )
                            Text(
                                text = "PRE-BRIEFING SUMMARY",
                                color = ColorSilver,
                                fontFamily = OrbitronFamily,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        
                        // Emotion Badge
                        val emotionColor = when (briefResponse.emotion.lowercase()) {
                            "focused" -> ColorUiGood
                            "tense" -> ColorUiWarn
                            else -> ColorSlate
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(emotionColor.copy(alpha = 0.2f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = briefResponse.emotion.uppercase(),
                                color = emotionColor,
                                fontFamily = RajdhaniFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    KerbStripe(modifier = Modifier.padding(top = 10.dp, bottom = 16.dp))

                    // ── Narrative ─────────────────────────────────────────────────
                    PitwallFrame(accentColor = ColorUiInfo, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = briefResponse.narrativeMd,
                            color = ColorSilver,
                            fontFamily = RajdhaniFamily,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    Spacer(Modifier.height(Dimens.SpaceLg))

                    // ── Focus & Danger Zones ──────────────────────────────────────
                    if (briefResponse.focus.isNotEmpty()) {
                        Text(
                            text = "TARGET CORNERS",
                            color = ColorSlate,
                            fontFamily = OrbitronFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(modifier = Modifier.padding(bottom = 16.dp)) {
                            briefResponse.focus.forEach { corner ->
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .background(ColorUiGood.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = corner,
                                        color = ColorUiGood,
                                        fontFamily = RajdhaniFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (briefResponse.dangerZonesToday.isNotEmpty()) {
                        Text(
                            text = "DANGER ZONES",
                            color = ColorUiBad,
                            fontFamily = OrbitronFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        briefResponse.dangerZonesToday.forEach { danger ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .background(ColorCharcoal, RoundedCornerShape(4.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(48.dp)
                                        .background(ColorUiBad)
                                )
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = ColorUiBad,
                                    modifier = Modifier.padding(start = 12.dp, end = 8.dp).size(20.dp)
                                )
                                Text(
                                    text = danger,
                                    color = ColorSilver,
                                    fontFamily = RajdhaniFamily,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 12.dp, end = 12.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(Dimens.SpaceLg))

                    // ── Web pre-brief CTA ──────────────────────────────
                    CyberButton(
                        text = "OPEN WEB PRE-BRIEF",
                        onClick = onOpenWebBrief,
                        variant = CyberButtonVariant.Outlined,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    )

                    Spacer(Modifier.height(Dimens.SpaceSm))

                    // ── Commence session CTA ────────────────────────────
                    CyberButton(
                        text = "🚀  COMMENCE TRACK SESSION",
                        onClick = onCommence,
                        variant = CyberButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    )

                    Spacer(Modifier.height(Dimens.SpaceXl))
                }
            } else {
                // Error state or empty
                Box(modifier = Modifier.fillMaxSize().weight(1f).padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Could not load briefing.", color = ColorUiBad, fontFamily = RajdhaniFamily)
                }
            }
        }

        // CRT overlay — on top of all content, non-interactive
        CrtOverlay()
    }
}
