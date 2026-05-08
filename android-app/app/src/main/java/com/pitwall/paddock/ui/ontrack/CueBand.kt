package com.pitwall.paddock.ui.ontrack

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.data.CueEvent
import com.pitwall.paddock.ui.components.PitwallFrame
import com.pitwall.paddock.ui.theme.*

@Composable
fun CueBand(
    cue: CueEvent?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = cue != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        if (cue != null) {
            val accent = when (cue.priority) {
                1 -> ColorUiGood
                2 -> ColorUiWarn
                else -> ColorUiBad
            }
            
            PitwallFrame(
                accentColor = accent,
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ColorInk.copy(alpha = 0.9f))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (cue.cornerId != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(accent.copy(alpha = 0.2f))
                                .border(1.dp, accent, RoundedCornerShape(4.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cue.cornerId.uppercase(),
                                color = accent,
                                fontFamily = OrbitronFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    
                    Text(
                        text = cue.text,
                        color = ColorSilver,
                        fontFamily = RajdhaniFamily,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp
                    )
                }
            }
        }
    }
}
