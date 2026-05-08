package com.pitwall.app.ui.components.pitwall

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitwall.app.ui.theme.LocalPitwallReducedMotion
import kotlinx.coroutines.delay

/**
 * Garage / analysis tile — border-forward card matching Vue CyberTile intent (title + kerb subcopy).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PitwallTileCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    entranceIndex: Int = 0,
) {
    val reduced = LocalPitwallReducedMotion.current
    var reveal by remember(reduced) { mutableStateOf(reduced) }

    LaunchedEffect(entranceIndex, reduced) {
        if (reduced) {
            reveal = true
        } else {
            delay((entranceIndex * 42).toLong())
            reveal = true
        }
    }

    AnimatedVisibility(
        visible = reveal,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color =
                MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = if (emphasized) 0.92f else if (enabled) 0.72f else 0.4f,
                ),
            border =
                BorderStroke(
                    width = 1.dp,
                    color =
                        when {
                            emphasized -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        },
                ),
            shadowElevation = if (emphasized) 5.dp else 1.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color =
                            if (enabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            },
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (emphasized && enabled) {
                    Text(
                        text = "▶",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}
