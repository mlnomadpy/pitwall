package com.pitwall.paddock.ui.feedback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.pitwall.paddock.BuildConfig
import com.pitwall.paddock.data.MockSonomaData
import com.pitwall.paddock.data.TrackAnalysisMock as T
import com.pitwall.paddock.ui.theme.PitwallBg
import com.pitwall.paddock.ui.theme.PitwallCyan
import com.pitwall.paddock.ui.theme.PitwallSurface
import com.pitwall.paddock.ui.theme.TextPrimary
import com.pitwall.paddock.ui.theme.TextSecondary

@Composable
fun HistoricalTrackMapScreen(
    onBack: () -> Unit,
) {
    var showGradient by remember { mutableStateOf(true) }
    var showDistribution by remember { mutableStateOf(true) }
    val poly = remember { MockSonomaData.trackPolyline }
    val speeds = remember(poly) { T.segmentSpeedsKmh(poly) }
    val turns = remember(poly) { T.turnLabelPositions(poly, count = 7) }
    val drs = remember(poly) { T.drsSegmentPolylines(poly) }
    val dist = T.speedDistribution()
    val elev = remember { T.elevationProfile(40) }
    val cam = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(MockSonomaData.defaultCenter, 14.2f)
    }
    val hasKey = BuildConfig.MAPS_API_KEY.isNotEmpty()
    val sectors = remember(poly) { sectorLabelPositions(poly) }
    val turnDistKm = remember(elev) { turnDistancesOnProfile(elev, turns) }

    Column(
        Modifier
            .fillMaxSize()
            .background(PitwallBg)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Text(
                "HISTORICAL TRACK MAP (ANALYSIS)",
                color = PitwallCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Speed gradient", color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Checkbox(
                    checked = showGradient,
                    onCheckedChange = { showGradient = it },
                    colors = CheckboxDefaults.colors(checkedColor = PitwallCyan),
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Speed distribution (pie on map)", color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Checkbox(
                    checked = showDistribution,
                    onCheckedChange = { showDistribution = it },
                    colors = CheckboxDefaults.colors(checkedColor = PitwallCyan),
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(220.dp)) {
                if (hasKey) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cam,
                        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
                    ) {
                        if (showGradient && speeds.isNotEmpty()) {
                            for (i in 0 until poly.size - 1) {
                                val c = T.speedToColor(speeds[i])
                                Polyline(
                                    points = listOf(poly[i], poly[i + 1]),
                                    color = c,
                                    width = 8f,
                                )
                            }
                        } else {
                            Polyline(
                                points = poly,
                                color = PitwallCyan,
                                width = 5f,
                            )
                        }
                        val drsGreen = androidx.compose.ui.graphics.Color(0.1f, 0.75f, 0.25f)
                        drs.forEach { seg ->
                            if (seg.size >= 2) {
                                Polyline(
                                    points = seg,
                                    color = drsGreen,
                                    width = 14f,
                                )
                            }
                        }
                        turns.forEach { (n, p) ->
                            key(n) {
                                val st = remember { MarkerState(p) }
                                val icon = remember {
                                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                                }
                                Marker(
                                    state = st,
                                    title = "T$n",
                                    icon = icon,
                                )
                            }
                        }
                        sectors.forEach { (label, p) ->
                            key(label) {
                                val st = remember { MarkerState(p) }
                                val icon = remember {
                                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                                }
                                Marker(
                                    state = st,
                                    title = label,
                                    icon = icon,
                                )
                            }
                        }
                    }
                    if (showDistribution) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
                            SpeedPie(
                                dist.first,
                                dist.second,
                                dist.third,
                                Modifier.padding(8.dp).size(72.dp),
                            )
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Add MAPS_API_KEY for speed-gradient map", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                SpeedGradientLegend(Modifier.width(32.dp).height(120.dp))
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("70 – 300 km/h", color = TextSecondary, fontSize = 9.sp)
                }
            }
            Text(
                "Circuit · elevation change ~${"%.1f".format(T.elevationRangeM)} m (mock)",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            ElevationChart(elev, turnDistKm, Modifier.height(120.dp).fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text("Yearly flag stats (mock)", color = PitwallCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            YearlyFlagsTable()
            Spacer(Modifier.height(8.dp))
            Text("Per-corner flags (sample)", color = TextSecondary, fontSize = 10.sp)
            CornerFlagsMiniTable()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SpeedPie(
    lowPct: Float,
    midPct: Float,
    highPct: Float,
    modifier: Modifier = Modifier,
) {
    val lowC = androidx.compose.ui.graphics.Color(0.9f, 0.2f, 0.2f)
    val midC = androidx.compose.ui.graphics.Color(0.9f, 0.8f, 0.1f)
    val highC = androidx.compose.ui.graphics.Color(0.2f, 0.4f, 0.95f)
    Canvas(modifier) {
        var start = -90f
        val s = minOf(360f, lowPct / 100f * 360f)
        val s2 = midPct / 100f * 360f
        val s3 = (360f - s - s2).coerceAtLeast(0f).coerceAtMost(highPct / 100f * 360f)
        val dim = this.size.minDimension
        val sz = Size(dim, dim)
        drawArc(lowC, start, s, useCenter = true, size = sz, topLeft = Offset.Zero)
        start += s
        drawArc(midC, start, s2, useCenter = true, size = sz, topLeft = Offset.Zero)
        start += s2
        drawArc(highC, start, s3, useCenter = true, size = sz, topLeft = Offset.Zero)
    }
}

@Composable
private fun SpeedGradientLegend(modifier: Modifier) {
    Column(modifier) {
        val steps = 12
        for (i in steps - 1 downTo 0) {
            val t = i / (steps - 1f)
            val c = T.speedToColor(70f + t * (300f - 70f))
            Spacer(
                Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .background(c),
            )
        }
    }
}

@Composable
private fun ElevationChart(
    samples: List<Pair<Float, Float>>,
    turnDistancesKm: List<Pair<Int, Float>>,
    modifier: Modifier,
) {
    if (samples.isEmpty()) return
    val dMax = samples.maxOf { it.first }
    val eMin = samples.minOf { it.second }
    val eMax = samples.maxOf { it.second }
    Box(
        modifier
            .background(PitwallSurface, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .padding(6.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val padL = 28f
            val padB = 18f
            val w = size.width - padL - 4f
            val h = size.height - padB - 8f
            val ed = (eMax - eMin).coerceAtLeast(0.1f)
            val path = androidx.compose.ui.graphics.Path()
            samples.forEachIndexed { i, p ->
                val x = padL + (p.first / dMax) * w
                val y = 4f + h * (1f - (p.second - eMin) / ed)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = PitwallCyan,
                style = Stroke(width = 2f, cap = StrokeCap.Round),
            )
            turnDistancesKm.forEach { (_, dk) ->
                if (dk in 0f..dMax) {
                    val x = padL + (dk / dMax) * w
                    drawLine(
                        color = TextSecondary.copy(alpha = 0.35f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f,
                    )
                }
            }
        }
    }
}

@Composable
private fun YearlyFlagsTable() {
    val years = listOf("2022" to Triple(2, 0, 0), "2023" to Triple(1, 1, 0), "2024" to Triple(3, 0, 1), "2025" to Triple(0, 0, 0))
    Column(Modifier.fillMaxWidth().background(PitwallSurface, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Year", "Yellow", "D-Yel", "SC").forEach { h ->
                Text(h, color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = TextSecondary.copy(alpha = 0.2f))
        years.forEach { (y, t) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(y, color = TextPrimary, fontSize = 10.sp, modifier = Modifier.width(40.dp))
                Text("${t.first}", color = TextPrimary, fontSize = 10.sp, modifier = Modifier.width(40.dp))
                Text("${t.second}", color = TextPrimary, fontSize = 10.sp, modifier = Modifier.width(40.dp))
                Text("${t.third}", color = TextPrimary, fontSize = 10.sp, modifier = Modifier.width(40.dp))
            }
        }
    }
}

@Composable
private fun CornerFlagsMiniTable() {
    val turnsN = (1..7).toList()
    Column(Modifier.fillMaxWidth().background(PitwallSurface, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            turnsN.take(4).forEach { t ->
                Text("T$t: 0", color = TextPrimary, fontSize = 9.sp, modifier = Modifier.width(64.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            turnsN.drop(4).forEach { t ->
                Text("T$t: 0", color = TextPrimary, fontSize = 9.sp, modifier = Modifier.width(64.dp))
            }
        }
    }
}

/** Approximate S1 / S2 / S3 along polyline. */
private fun sectorLabelPositions(poly: List<LatLng>): List<Pair<String, LatLng>> {
    if (poly.isEmpty()) return emptyList()
    val open = if (poly.size > 1 && poly.first() == poly.last()) poly.dropLast(1) else poly
    if (open.isEmpty()) return emptyList()
    fun at(t: Float): LatLng {
        val idx = (t * (open.size - 1)).toInt().coerceIn(0, open.size - 1)
        return open[idx]
    }
    return listOf("S1" to at(0.2f), "S2" to at(0.5f), "S3" to at(0.82f))
}

/**
 * Map turn number → approximate distance (km) along lap for chart guides.
 * Uses equal spacing of [TrackAnalysisMock.trackLengthKm] for mock.
 */
private fun turnDistancesOnProfile(
    profile: List<Pair<Float, Float>>,
    turns: List<Pair<Int, LatLng>>,
): List<Pair<Int, Float>> {
    if (turns.isEmpty() || profile.isEmpty()) return emptyList()
    val dMax = profile.maxOf { it.first }
    val n = turns.size.coerceAtLeast(1)
    return turns.map { (num, _) ->
        val fraction = (num - 0.5f) / n
        num to (dMax * fraction)
    }
}
