package com.pitwall.paddock.ui.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.data.DemoContent
import com.pitwall.paddock.data.LeaderboardEntry
import com.pitwall.paddock.ui.components.CrtOverlay
import com.pitwall.paddock.ui.components.CyberButton
import com.pitwall.paddock.ui.components.GlowText
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
fun RankingScreen(
    onOpenPostSessionCatalog: () -> Unit = {},
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
                Spacer(Modifier.height(Dimens.SpaceSm))

                // ── Heading with glow ─────────────────────────────────────────
                GlowText(
                    text      = "MARKER MASTERY",
                    color     = ColorSilver,
                    glowColor = ColorUiGood,
                    style     = androidx.compose.ui.text.TextStyle(
                        fontFamily = OrbitronFamily,
                        fontWeight = FontWeight.Black,
                        fontSize   = 22.sp,
                    ),
                )

                // ── Live session badge ────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    StatusPip(active = true, color = ColorBiosGreen)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text          = "LIVE SESSION  ·  TURN 11 APEX FOCUS",
                        color         = ColorSlate,
                        fontFamily    = RajdhaniFamily,
                        fontSize      = 10.sp,
                        fontWeight    = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                    )
                }

                Spacer(Modifier.height(Dimens.SpaceMd))

                // ── Session time card ─────────────────────────────────────────
                PitwallFrame(accentColor = ColorUiGood) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment       = Alignment.CenterVertically,
                        horizontalArrangement   = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text          = "SESSION TIME",
                            color         = ColorSlate.copy(alpha = 0.7f),
                            fontFamily    = RajdhaniFamily,
                            fontSize      = 10.sp,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            text       = "14:32:05",
                            color      = ColorUiGood,
                            fontFamily = ShareTechMonoFamily,
                            fontSize   = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.height(Dimens.SpaceLg))

                // ── Leaderboard header ────────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        text          = "GLOBAL RANKING",
                        color         = ColorSilver,
                        fontFamily    = RajdhaniFamily,
                        fontSize      = 12.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text       = "UPDATED LIVE",
                        color      = ColorUiGood,
                        fontFamily = RajdhaniFamily,
                        fontSize   = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(8.dp))
                LeaderboardTable(DemoContent.leaderboard)

                Spacer(Modifier.height(Dimens.SpaceLg))
                GemmaCard()

                Spacer(Modifier.height(Dimens.SpaceMd))
                SectorFocusCard()

                Spacer(Modifier.height(Dimens.SpaceMd))

                CyberButton(
                    text     = "POST-RACE ANALYSIS CATALOG",
                    onClick  = onOpenPostSessionCatalog,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(Dimens.SpaceXl))
            }
        }

        CrtOverlay()
    }
}

// ── Leaderboard ───────────────────────────────────────────────────────────────

@Composable
private fun LeaderboardTable(rows: List<LeaderboardEntry>) {
    PitwallFrame {
        Column(Modifier.fillMaxWidth()) {
            // Header row
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("POS",        color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.width(32.dp))
                Text("DRIVER / TEAM", color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1.2f))
                Text("MASTERY",    color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
                Text("GAP",        color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
            }
            HorizontalDivider(
                modifier  = Modifier.padding(vertical = 8.dp),
                color     = ColorSlate.copy(alpha = 0.2f),
                thickness = Dimens.BorderNormal,
            )
            rows.forEach { LeaderboardRow(it) }
        }
    }
}

@Composable
private fun LeaderboardRow(row: LeaderboardEntry) {
    val highlighted = row.isHighlighted
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                color = if (highlighted) ColorUiGood.copy(alpha = 0.06f) else ColorInk.copy(alpha = 0.4f),
                shape = RoundedCornerShape(Dimens.CardCornerSm),
            )
            .border(
                width = Dimens.BorderNormal,
                color = if (highlighted) ColorUiGood.copy(alpha = 0.5f) else ColorSlate.copy(alpha = 0.1f),
                shape = RoundedCornerShape(Dimens.CardCornerSm),
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Position
        Text(row.pos, color = ColorUiGood, fontFamily = OrbitronFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(32.dp))
        // Driver + team
        Row(Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).background(ColorSlate.copy(alpha = 0.15f), CircleShape))
            Column(Modifier.padding(start = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.name, color = ColorSilver, fontFamily = RajdhaniFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (row.badge != null) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text     = row.badge,
                            color    = ColorUiGood,
                            fontFamily = RajdhaniFamily,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .border(0.5.dp, ColorUiGood, RoundedCornerShape(2.dp))
                                .padding(horizontal = 2.dp),
                        )
                    }
                }
                Text(row.team, color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 10.sp)
            }
        }
        Text(row.score, color = ColorSilver, fontFamily = ShareTechMonoFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp), textAlign = TextAlign.End, maxLines = 1)
        Text(row.gap,   color = ColorSlate,  fontFamily = ShareTechMonoFamily, fontSize = 10.sp, modifier = Modifier.width(48.dp), textAlign = TextAlign.End, maxLines = 1)
    }
}

// ── Gemma Insights card ───────────────────────────────────────────────────────

@Composable
private fun GemmaCard() {
    PitwallFrame(accentColor = ColorUiInfo) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Psychology, contentDescription = null, tint = ColorUiInfo, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("GEMMA INSIGHTS", color = ColorSilver, fontFamily = OrbitronFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text      = "ANALYSIS: ALPHA DRIVER",
                color     = ColorSlate,
                fontFamily = RajdhaniFamily,
                fontSize  = 9.sp,
                letterSpacing = 0.5.sp,
                modifier  = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            Text(
                text       = "J. Vane achieved the ALPHA rating by maintaining a 98% apex proximity variance across 15 laps at Turn 11.",
                color      = ColorSilver,
                fontFamily = RajdhaniFamily,
                fontSize   = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(Dimens.SpaceMd))

            Text("TRAJECTORY CONSISTENCY", color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 9.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress    = { 0.985f },
                modifier    = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(2.dp)),
                color       = ColorBiosGreen,
                trackColor  = ColorSlate.copy(alpha = 0.2f),
            )

            Spacer(Modifier.height(Dimens.SpaceSm))
            Text("BRAKING COMMITMENT", color = ColorSlate, fontFamily = RajdhaniFamily, fontSize = 9.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress    = { 0.942f },
                modifier    = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(2.dp)),
                color       = ColorUiGood,
                trackColor  = ColorSlate.copy(alpha = 0.2f),
            )
        }
    }
}

// ── Sector Focus card ─────────────────────────────────────────────────────────

@Composable
private fun SectorFocusCard() {
    PitwallFrame {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SECTOR 3 FOCUS", color = ColorSilver, fontFamily = OrbitronFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("⬡", color = ColorUiGood, fontSize = 14.sp)
            }
            Spacer(Modifier.height(Dimens.SpaceMd))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(ColorInk, RoundedCornerShape(Dimens.CardCornerSm))
                    .border(Dimens.BorderNormal, ColorSlate.copy(alpha = 0.15f), RoundedCornerShape(Dimens.CardCornerSm)),
                contentAlignment = Alignment.BottomStart,
            ) {
                Box(Modifier.fillMaxWidth(0.35f).height(4.dp).background(ColorBiosGreen, RoundedCornerShape(2.dp)))
                Text(
                    text      = "T11 DEGRADATION",
                    color     = ColorSlate,
                    fontFamily = RajdhaniFamily,
                    fontSize  = 9.sp,
                    modifier  = Modifier.padding(8.dp).align(Alignment.TopEnd),
                )
            }
        }
    }
}
