package com.pitwall.paddock.ui.briefing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.data.BriefingCardData
import com.pitwall.paddock.data.DemoContent
import com.pitwall.paddock.data.BriefingCardStyle
import com.pitwall.paddock.data.toAccentColor
import com.pitwall.paddock.ui.components.PitwallTopBar
import com.pitwall.paddock.ui.theme.PitwallBg
import com.pitwall.paddock.ui.theme.PitwallCyan
import com.pitwall.paddock.ui.theme.PitwallSurface
import com.pitwall.paddock.ui.theme.TextPrimary
import com.pitwall.paddock.ui.theme.TextSecondary

@Composable
fun BriefingScreen(
    onCommence: () -> Unit,
    onOpenWebBrief: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(PitwallBg)
            .verticalScroll(rememberScrollState()),
    ) {
        PitwallTopBar()
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(8.dp))
            Text(
                "SESSION PREP",
                color = PitwallCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
            )
            Text(
                "PRE-BRIEFING SUMMARY",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "Review targeted sectors and telemetry markers prior to session initiation. Data derived from previous stint anomalies.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(Modifier.height(20.dp))
            DemoContent.briefingCards.forEach { card ->
                BriefingSectorCard(data = card)
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onOpenWebBrief,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PitwallCyan),
                border = BorderStroke(1.5.dp, PitwallCyan),
            ) {
                Text(
                    "OPEN WEB PRE-BRIEF (TRACK NAVIGATION)",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onCommence,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PitwallCyan),
                border = BorderStroke(1.5.dp, PitwallCyan),
            ) {
                Text("🚀 ", fontSize = 16.sp)
                Text(
                    "COMMENCE TRACK SESSION",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BriefingSectorCard(
    data: BriefingCardData,
) {
    val accent = data.style.toAccentColor()
    val icon = when (data.style) {
        BriefingCardStyle.Green -> Icons.Filled.WarningAmber
        BriefingCardStyle.Peach -> Icons.Filled.Bolt
        BriefingCardStyle.Cyan -> Icons.Filled.Star
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .background(PitwallSurface, RoundedCornerShape(10.dp))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .height(IntrinsicSize.Min),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent, RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)),
        )
        Row(
            Modifier
                .weight(1f)
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(PitwallBg, RoundedCornerShape(8.dp))
                    .border(1.dp, TextSecondary.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(
                    data.mkrCode,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    data.title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                )
                Text(
                    data.body,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
