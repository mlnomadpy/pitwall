package com.pitwall.paddock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.ui.theme.PitwallBg
import com.pitwall.paddock.ui.theme.PitwallCyan
import com.pitwall.paddock.ui.theme.TextSecondary

@Composable
fun PitwallTopBar(
    title: String = "SONOMA RACEWAY",
    modifier: Modifier = Modifier,
    onProfileClick: () -> Unit = {},
    onFilterClick: () -> Unit = {},
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(PitwallBg)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        IconButton(
            onClick = onProfileClick,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(TextSecondary.copy(alpha = 0.3f), CircleShape),
            )
        }
        Text(
            text = title,
            color = PitwallCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 56.dp),
        )
        IconButton(
            onClick = onFilterClick,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "Filter",
                tint = PitwallCyan,
            )
        }
    }
}
