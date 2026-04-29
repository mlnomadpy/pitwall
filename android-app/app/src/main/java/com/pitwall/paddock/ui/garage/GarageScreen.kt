package com.pitwall.paddock.ui.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwall.paddock.PaddockUiState
import com.pitwall.paddock.data.TrackMarker
import com.pitwall.paddock.ui.components.PitwallTopBar
import com.pitwall.paddock.ui.ontrack.OnTrackLocationCard
import com.pitwall.paddock.ui.theme.PitwallBg
import com.pitwall.paddock.ui.theme.PitwallSurface
import com.pitwall.paddock.ui.theme.PitwallCyan
import com.pitwall.paddock.ui.theme.TextPrimary
import com.pitwall.paddock.ui.theme.TextSecondary

@Composable
fun GarageScreen(
    state: PaddockUiState,
    baseUrl: String,
    markers: List<TrackMarker>,
    onRefreshBridge: () -> Unit = {},
    onOpenPostSessionCatalog: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(PitwallBg)
            .verticalScroll(rememberScrollState()),
    ) {
        PitwallTopBar()
        Text(
            "GARAGE",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(20.dp, 12.dp, 20.dp, 0.dp),
        )
        Text("Bridge & dev", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.bridgeLine,
                color = PitwallCyan,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier
                    .weight(1f)
                    .background(PitwallSurface, RoundedCornerShape(8.dp))
                    .padding(12.dp),
            )
            TextButton(onClick = onRefreshBridge) { Text("↻", color = PitwallCyan) }
        }
        Spacer(Modifier.height(12.dp))
        Text("API base", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            baseUrl,
            color = TextPrimary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Text(
            "Set PITWALL_API_BASE_URL in local.properties. Emulator: 10.0.2.2:8765",
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = onOpenPostSessionCatalog,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            Text(
                "Post-race / session feedback — full module catalog",
                color = PitwallCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        OnTrackLocationCard(state = state, markers = markers)
    }
}
