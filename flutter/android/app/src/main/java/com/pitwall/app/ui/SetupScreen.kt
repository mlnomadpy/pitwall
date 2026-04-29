package com.pitwall.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.app.ui.theme.PitwallColors

@Composable
fun SetupScreen(
    uiState: PitwallUiState,
    onStartSession: (replayPath: String?, level: String) -> Unit,
    onLevelChange: (String) -> Unit,
) {
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { onStartSession(it.toString(), uiState.driverLevel) } }

    // Full-size scrollable container — avoids the Box+verticalScroll touch clip bug in landscape
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(PitwallColors.Background),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center,
        ) {
            // ── Logo ──────────────────────────────────────────────────────────
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = PitwallColors.GripGreen, fontWeight = FontWeight.Black)) {
                        append("PIT")
                    }
                    withStyle(SpanStyle(color = PitwallColors.TextPrimary, fontWeight = FontWeight.Black)) {
                        append("WALL")
                    }
                },
                fontSize = 44.sp,
                letterSpacing = (-2).sp,
            )
            Text(
                "AI Racing Coach — Sonoma Raceway",
                color = PitwallColors.TextDim,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
            )

            Spacer(Modifier.height(36.dp))

            // ── Driver Level ──────────────────────────────────────────────────
            SectionLabel("DRIVER LEVEL")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("beginner", "intermediate", "pro").forEach { level ->
                    val selected = uiState.driverLevel == level
                    FilterChip(
                        selected = selected,
                        onClick = { onLevelChange(level) },
                        label = {
                            Text(
                                level.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PitwallColors.GripGreen.copy(alpha = 0.15f),
                            selectedLabelColor = PitwallColors.GripGreen,
                            containerColor = PitwallColors.Surface,
                            labelColor = PitwallColors.TextDim,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            selectedBorderColor = PitwallColors.GripGreen,
                            borderColor = PitwallColors.Border,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Hardware Status (only real sensors) ───────────────────────────
            SectionLabel("HARDWARE")
            Spacer(Modifier.height(8.dp))
            listOf(
                "Racelogic Mini" to true,
                "OBDLink MX"     to true,
                "Pixel Earbuds"  to true,
            ).forEach { (name, ok) -> StatusRow(name, ok) }

            Spacer(Modifier.height(28.dp))

            // ── Buttons ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PrimaryButton(
                    label = "START SESSION",
                    icon = Icons.Default.Flag,
                    loading = uiState.isStarting,
                    modifier = Modifier.weight(1f),
                    onClick = { onStartSession(null, uiState.driverLevel) },
                )
                SecondaryButton(
                    label = "REPLAY VBO",
                    icon = Icons.Default.Replay,
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                )
            }
        }
    }
}

// ── Small shared components ───────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) = Text(
    text,
    color = PitwallColors.TextDim,
    fontSize = 10.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 2.sp,
)

@Composable
private fun StatusRow(label: String, connected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Surface(
            modifier = Modifier.size(7.dp),
            shape = RoundedCornerShape(50),
            color = if (connected) PitwallColors.GripGreen else PitwallColors.TextDim,
        ) {}
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = if (connected) PitwallColors.TextPrimary else PitwallColors.TextDim,
            fontSize = 13.sp,
        )
    }
}

@Composable
fun PrimaryButton(
    label: String,
    icon: ImageVector,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = !loading,
        modifier = modifier.height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PitwallColors.GripGreen,
            contentColor = Color.Black,
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = Color.Black,
            )
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            fontSize = 12.sp,
        )
    }
}

@Composable
fun SecondaryButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = PitwallColors.TextSecondary,
        ),
        border = BorderStroke(1.dp, SolidColor(PitwallColors.Border)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            fontSize = 11.sp,
        )
    }
}
