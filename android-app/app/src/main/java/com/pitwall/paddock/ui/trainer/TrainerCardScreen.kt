package com.pitwall.paddock.ui.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.data.SaveSlot
import com.pitwall.paddock.ui.components.CrtOverlay
import com.pitwall.paddock.ui.components.PitwallTopBar
import com.pitwall.paddock.ui.theme.ColorInk
import com.pitwall.paddock.ui.theme.ColorSlate
import com.pitwall.paddock.ui.theme.RajdhaniFamily

@Composable
fun TrainerCardScreen(activeSlot: SaveSlot?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorInk)
    ) {
        CrtOverlay(modifier = Modifier.fillMaxSize())
        Column(modifier = Modifier.fillMaxSize()) {
            PitwallTopBar(title = "TRAINER CARD")
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Trainer Card for ${activeSlot?.driverName ?: "Unknown"}\nComing in next phase.",
                    color = ColorSlate,
                    fontFamily = RajdhaniFamily,
                    fontSize = 16.sp
                )
            }
        }
    }
}
