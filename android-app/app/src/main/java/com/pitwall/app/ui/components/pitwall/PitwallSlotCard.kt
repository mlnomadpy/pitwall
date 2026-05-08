package com.pitwall.app.ui.components.pitwall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.pitwall.app.entities.save.SaveSlot
import com.pitwall.app.ui.theme.PitwallPalette

/**
 * Save-slot card echoing Vue [SlotCard] — gradient kerb border, filled vs empty affordances.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PitwallSlotCard(
    slotId: Int,
    slot: SaveSlot?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val borderBrush: Brush =
        if (slot != null) {
            Brush.linearGradient(
                listOf(
                    PitwallPalette.CyanPrimary.copy(alpha = 0.95f),
                    PitwallPalette.CurbRed.copy(alpha = 0.55f),
                ),
            )
        } else {
            Brush.linearGradient(
                listOf(
                    PitwallPalette.Slate.copy(alpha = 0.45f),
                    PitwallPalette.Slate.copy(alpha = 0.15f),
                ),
            )
        }

    Surface(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .border(BorderStroke(2.dp, borderBrush), shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 4.dp,
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                text = "SLOT $slotId",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (slot == null) {
                Text(
                    text = "EMPTY",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = "Tap to create driver",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Text(
                    text = slot.driverName,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text =
                        "${slot.skillLevel.uppercase()} · LV ${slot.level} · ${slot.car}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = "Coach · ${slot.preferredCoach.uppercase()} · ${slot.preferredTrack.uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
