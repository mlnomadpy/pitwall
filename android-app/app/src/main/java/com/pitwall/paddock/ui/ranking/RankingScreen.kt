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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import com.pitwall.paddock.ui.components.PitwallTopBar
import com.pitwall.paddock.ui.theme.AccentGreen
import com.pitwall.paddock.ui.theme.PitwallBg
import com.pitwall.paddock.ui.theme.PitwallCyan
import com.pitwall.paddock.ui.theme.PitwallSurface
import com.pitwall.paddock.ui.theme.TextPrimary
import com.pitwall.paddock.ui.theme.TextSecondary

@Composable
fun RankingScreen(
    onOpenPostSessionCatalog: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(PitwallBg)
            .verticalScroll(rememberScrollState()),
    ) {
        PitwallTopBar()
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(4.dp))
            Text(
                "MARKER MASTERY",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(AccentGreen))
                Spacer(Modifier.width(6.dp))
                Text(
                    "LIVE SESSION // TURN 11 APEX FOCUS",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .border(1.dp, PitwallCyan, RoundedCornerShape(6.dp))
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("SESSION TIME", color = TextSecondary, fontSize = 10.sp, letterSpacing = 0.8.sp)
                    Text("14:32:05", color = PitwallCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "GLOBAL RANKING",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Spacer(Modifier)
                Text("UPDATED LIVE", color = PitwallCyan, fontSize = 9.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
            LeaderboardTable(DemoContent.leaderboard)
            Spacer(Modifier.height(20.dp))
            GemmaCard()
            Spacer(Modifier.height(16.dp))
            SectorFocusCard()
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onOpenPostSessionCatalog,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                Text(
                    "Post-race analysis catalog (temp, weather, tires, telemetry…)",
                    color = PitwallCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LeaderboardTable(rows: List<LeaderboardEntry>) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(PitwallSurface, RoundedCornerShape(8.dp))
            .border(1.dp, com.pitwall.paddock.ui.theme.CardStroke, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("POS", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
            Text("DRIVER / TEAM", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
            Text("MASTERY", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
            Text("GAP", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = TextSecondary.copy(alpha = 0.2f))
        rows.forEach { r ->
            LeaderboardRow(r)
        }
    }
}

@Composable
private fun LeaderboardRow(row: LeaderboardEntry) {
    val border = if (row.isHighlighted) PitwallCyan else TextSecondary.copy(alpha = 0.1f)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .background(if (row.isHighlighted) PitwallCyan.copy(alpha = 0.05f) else com.pitwall.paddock.ui.theme.PitwallBg, RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.pos, color = PitwallCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(32.dp))
        Row(Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).background(TextSecondary.copy(alpha = 0.2f), CircleShape))
            Column(Modifier.padding(start = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (row.badge != null) {
                        Spacer(Modifier.width(4.dp))
                        Text(row.badge, color = PitwallCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.border(0.5.dp, PitwallCyan, RoundedCornerShape(2.dp)).padding(2.dp, 0.dp, 2.dp, 0.dp))
                    }
                }
                Text(row.team, color = TextSecondary, fontSize = 10.sp)
            }
        }
        Text(row.score, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp), textAlign = TextAlign.End, maxLines = 1)
        Text(row.gap, color = TextSecondary, fontSize = 10.sp, modifier = Modifier.width(48.dp), textAlign = TextAlign.End, maxLines = 1)
    }
}

@Composable
private fun GemmaCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(PitwallSurface, RoundedCornerShape(10.dp))
            .border(1.dp, PitwallCyan.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = PitwallCyan,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("GEMMA INSIGHTS", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text("ANALYSIS: ALPHA DRIVER", color = TextSecondary, fontSize = 9.sp, letterSpacing = 0.5.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
        Text(
            "J. Vane achieved the ALPHA rating by maintaining a 98% apex proximity variance across 15 laps at Turn 11.",
            color = TextPrimary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text("TRAJECTORY CONSISTENCY", color = TextSecondary, fontSize = 9.sp)
        LinearProgressIndicator(
            progress = { 0.985f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(2.dp)),
            color = AccentGreen,
            trackColor = TextSecondary.copy(alpha = 0.2f),
        )
        Spacer(Modifier.height(8.dp))
        Text("BRAKING COMMITMENT", color = TextSecondary, fontSize = 9.sp)
        LinearProgressIndicator(
            progress = { 0.942f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(2.dp)),
            color = PitwallCyan,
            trackColor = TextSecondary.copy(alpha = 0.2f),
        )
    }
}

@Composable
private fun SectorFocusCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(PitwallSurface, RoundedCornerShape(8.dp))
            .border(1.dp, com.pitwall.paddock.ui.theme.CardStroke, RoundedCornerShape(8.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SECTOR 3 FOCUS", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("⬡", color = PitwallCyan, fontSize = 14.sp)
        }
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(PitwallBg, RoundedCornerShape(4.dp))
                .border(1.dp, TextSecondary.copy(alpha = 0.1f), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.35f)
                    .align(Alignment.BottomStart)
                    .height(4.dp)
                    .background(AccentGreen),
            )
            Text("T11 DEGRADATION", color = TextSecondary, fontSize = 9.sp, modifier = Modifier.padding(8.dp).align(Alignment.TopEnd))
        }
    }
}
