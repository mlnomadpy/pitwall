package com.pitwall.paddock.ui.marker

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.data.TrackMarker
import com.pitwall.paddock.ui.theme.AccentGreen
import com.pitwall.paddock.ui.theme.AccentRed
import com.pitwall.paddock.ui.theme.PitwallBg
import com.pitwall.paddock.ui.theme.PitwallCyan
import com.pitwall.paddock.ui.theme.PitwallSurface
import com.pitwall.paddock.ui.theme.TextPrimary
import com.pitwall.paddock.ui.theme.TextSecondary

@Composable
fun MarkerDetailScreen(
    marker: TrackMarker,
    isInFocus: Boolean,
    canAddToFocus: Boolean,
    onBack: () -> Unit,
    onTackle: () -> Unit,
) {
    val showHard = marker.difficulty >= 4
    Column(
        Modifier
            .fillMaxSize()
            .background(PitwallBg)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = marker.shortTitle.replace(" ", "\n"),
                color = PitwallCyan,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 28.sp,
                letterSpacing = 0.5.sp,
            )
            if (showHard) {
                Box(
                    modifier = Modifier
                        .border(1.dp, AccentRed, RoundedCornerShape(4.dp))
                        .background(PitwallSurface, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠ ", fontSize = 10.sp, color = AccentRed)
                        Text("HARD", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Videocam, null, tint = TextPrimary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text("REPLAY TELEMETRY", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(horizontal = 20.dp)
                .background(PitwallSurface, RoundedCornerShape(8.dp))
                .border(1.dp, TextSecondary.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayCircle,
                "Play",
                modifier = Modifier.size(56.dp),
                tint = PitwallCyan.copy(alpha = 0.7f),
            )
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp)) {
                LinearProgressIndicator(
                    progress = { 0.3f },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(1.dp)),
                    color = PitwallCyan,
                    trackColor = TextSecondary.copy(alpha = 0.2f),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SupportAgent, null, tint = PitwallCyan, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(8.dp))
            Text("ROSS COACHING NOTES", color = PitwallCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        }
        Row(
            Modifier
                .padding(horizontal = 20.dp)
                .background(PitwallSurface, RoundedCornerShape(6.dp))
                .border(1.dp, TextSecondary.copy(alpha = 0.1f), RoundedCornerShape(6.dp)),
        ) {
            Box(Modifier.width(3.dp).height(100.dp).background(PitwallCyan))
            Text(
                "“${marker.coaching}”",
                color = TextPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(12.dp).weight(1f),
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TelemetryPill("ENTRY SPD", "${marker.entrySpeedKph}", "KPH", isGreen = true)
            TelemetryPill("APEX G", "${marker.apexG}", "G", isGreen = false)
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onTackle,
            enabled = isInFocus || canAddToFocus,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PitwallCyan, contentColor = PitwallBg),
        ) {
            Icon(Icons.Filled.Flag, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    isInFocus -> "IN FOCUS"
                    !canAddToFocus -> "MAX 3 FOCUS"
                    else -> "TACKLE THIS MARKER"
                },
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
            )
        }
        if (isInFocus) {
            Text(
                "In your pick-3 focus set for this session.",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TelemetryPill(
    label: String,
    value: String,
    unit: String,
    isGreen: Boolean,
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, color = TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = if (isGreen) AccentGreen else AccentRed,
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
            )
            Text(" $unit", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        }
    }
}
