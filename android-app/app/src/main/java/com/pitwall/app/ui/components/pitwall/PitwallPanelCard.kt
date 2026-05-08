package com.pitwall.app.ui.components.pitwall

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pitwall.app.ui.theme.LocalPitwallReducedMotion
import kotlinx.coroutines.delay

/**
 * Same chrome as [PitwallTileCard] but hosts arbitrary content (e.g. FilterChip rows) — no outer tap target.
 */
@Composable
fun PitwallPanelCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    entranceIndex: Int = 0,
    content: @Composable ColumnScope.() -> Unit,
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
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
            tonalElevation = 2.dp,
            shadowElevation = 1.dp,
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                }
                content()
            }
        }
    }
}
