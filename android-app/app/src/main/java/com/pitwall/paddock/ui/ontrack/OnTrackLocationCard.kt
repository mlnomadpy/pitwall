package com.pitwall.paddock.ui.ontrack

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.pitwall.paddock.PaddockUiState
import com.pitwall.paddock.data.TrackMarker
import com.pitwall.paddock.ui.theme.PitwallSurface
import com.pitwall.paddock.ui.theme.TextPrimary
import com.pitwall.paddock.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun OnTrackLocationCard(
    state: PaddockUiState,
    markers: List<TrackMarker>,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    var locText by remember { mutableStateOf("—") }
    var nearest by remember { mutableStateOf("—") }

    LaunchedEffect(hasPermission, state.selectedMarkerIds) {
        if (hasPermission) {
            @SuppressLint("MissingPermission")
            val client = LocationServices.getFusedLocationProviderClient(context)
            client.lastLocation.addOnSuccessListener { loc: Location? ->
                if (loc == null) {
                    locText = "No fix yet (move to open sky)"
                } else {
                    locText = "± ${loc.latitude}, ${loc.longitude} @ ${(loc.accuracy).roundToInt()}m"
                    if (state.selectedMarkerIds.isNotEmpty()) {
                        var best: TrackMarker? = null
                        var dMin = Float.MAX_VALUE
                        for (id in state.selectedMarkerIds) {
                            val m = markers.find { it.id == id } ?: continue
                            val a = floatArrayOf(0f)
                            Location.distanceBetween(
                                loc.latitude, loc.longitude,
                                m.position.latitude, m.position.longitude,
                                a,
                            )
                            if (a[0] < dMin) {
                                dMin = a[0]
                                best = m
                            }
                        }
                        nearest = if (best != null) "Nearest focus: ${best.id} — ${dMin.roundToInt()} m" else "—"
                    } else {
                        nearest = "No focus markers — pick up to 3 on the map"
                    }
                }
            }
        } else {
            locText = "Location denied"
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text("LOCATION (ON TRACK)", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .then(Modifier),
        ) {
            Text("Live position", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = locText, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
            Text(text = nearest, color = TextSecondary, fontSize = 11.sp)
            if (!hasPermission) {
                Button(
                    onClick = { permission.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("Grant location")
                }
            }
        }
    }
}
