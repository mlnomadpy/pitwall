package com.pitwall.paddock.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.ui.components.CrtOverlay
import com.pitwall.paddock.ui.components.PitwallTopBar
import com.pitwall.paddock.ui.theme.ColorInk
import com.pitwall.paddock.ui.theme.ColorSlate
import com.pitwall.paddock.ui.theme.RajdhaniFamily

@Composable
fun CoachSelectScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorInk)
    ) {
        CrtOverlay(modifier = Modifier.fillMaxSize())
        Column(modifier = Modifier.fillMaxSize()) {
            PitwallTopBar(title = "COACH SELECT")
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Coach Select coming in next phase.",
                    color = ColorSlate,
                    fontFamily = RajdhaniFamily,
                    fontSize = 16.sp
                )
            }
        }
    }
}
