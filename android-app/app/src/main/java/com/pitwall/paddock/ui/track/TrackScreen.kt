package com.pitwall.paddock.ui.track

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.pitwall.paddock.BuildConfig
import com.pitwall.paddock.PaddockUiState
import com.pitwall.paddock.data.MockSonomaData
import com.pitwall.paddock.data.TrackMarker
import com.pitwall.paddock.data.difficultyLabel
import com.pitwall.paddock.ui.components.PitwallTopBar
import com.pitwall.paddock.ui.theme.AccentPink
import com.pitwall.paddock.ui.theme.PitwallBg
import com.pitwall.paddock.ui.theme.PitwallCyan
import com.pitwall.paddock.ui.theme.PitwallSurface
import com.pitwall.paddock.ui.theme.TextPrimary
import com.pitwall.paddock.ui.theme.TextSecondary

@Composable
fun TrackScreen(
    state: PaddockUiState,
    markers: List<TrackMarker>,
    onOpenMarkerDetail: (TrackMarker) -> Unit,
    onSelectedCountClick: () -> Unit,
) {
    var mapSelected by remember { mutableStateOf<TrackMarker?>(null) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(MockSonomaData.defaultCenter, 14.3f)
    }
    val hasMapsKey = BuildConfig.MAPS_API_KEY.isNotEmpty()
    val trackLine = remember { MockSonomaData.trackPolyline }

    Column(Modifier.fillMaxSize().background(PitwallBg)) {
        PitwallTopBar()
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (hasMapsKey) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
                ) {
                    Polyline(
                        points = trackLine,
                        color = PitwallCyan,
                        width = 5f,
                        pattern = listOf(Dash(18f), Gap(12f)),
                    )
                    markers.forEach { m ->
                        val st = remember(m.id) { MarkerState(m.position) }
                        val icon = remember(m.id) { BitmapDescriptorFactory.defaultMarker(m.mapHue) }
                        Marker(
                            state = st,
                            title = m.shortTitle,
                            icon = icon,
                            onClick = {
                                mapSelected = m
                                true
                            },
                        )
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Add MAPS_API_KEY in local.properties",
                        color = TextSecondary,
                    )
                }
            }

            mapSelected?.let { m ->
                MapMarkerTooltip(
                    marker = m,
                    onView = { onOpenMarkerDetail(m) },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp, start = 16.dp, end = 16.dp),
                )
            }
        }

        val n = state.selectedMarkerIds.size
        OutlinedCta(
            text = if (n == 0) "0 SELECTED" else "$n SELECTED",
            onClick = onSelectedCountClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 8.dp, 16.dp, 4.dp),
        )
    }
}

@Composable
private fun MapMarkerTooltip(
    marker: TrackMarker,
    onView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .background(PitwallSurface, RoundedCornerShape(8.dp))
            .border(1.dp, AccentPink.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    marker.shortTitle,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    Icons.Default.Warning,
                    null,
                    tint = AccentPink,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(16.dp),
                )
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 3.dp, height = 14.dp)
                        .background(AccentPink, RoundedCornerShape(1.dp)),
                )
                Text(
                    marker.difficultyLabel(),
                    color = AccentPink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text(
                marker.tooltipSubtitle,
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        TextButton(onClick = onView) {
            Text("VIEW", color = PitwallCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun OutlinedCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = PitwallCyan,
        ),
        border = BorderStroke(1.5.dp, PitwallCyan),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(PitwallCyan, RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = PitwallCyan,
            )
        }
    }
}
