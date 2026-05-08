package com.pitwall.app.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pitwall.app.data.remote.LapTimeDistributionDto
import com.pitwall.app.di.SessionHolder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LapDistributionScreen(
    navController: NavController,
    vm: LapDistributionViewModel = viewModel(),
) {
    val dist by vm.dist.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val sid = SessionHolder.activeSessionId

    LaunchedEffect(sid) {
        if (!sid.isNullOrBlank()) {
            vm.load(sid)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lap time spread") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            sid.isNullOrBlank() ->
                Text(
                    "No active session. Pick one under Sessions.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(padding).padding(16.dp),
                )
            loading ->
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .padding(padding)
                            .padding(24.dp),
                )
            error != null ->
                Text(
                    error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(padding).padding(16.dp),
                )
            dist != null -> {
                val d = dist!!
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            "${d.lapCount} laps · μ ${d.meanS}s · σ ${d.stddevS}s",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    item {
                        LapTimeBoxPlot(
                            dist = d,
                            whiskerColor = MaterialTheme.colorScheme.outline,
                            boxFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            boxOutline = MaterialTheme.colorScheme.primary,
                            medianColor = MaterialTheme.colorScheme.primary,
                            outlierColor = MaterialTheme.colorScheme.error,
                        )
                    }
                    item {
                        Card(colors = CardDefaults.cardColors()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Tukey fences", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "Whiskers ${d.whiskerLowS}s → ${d.whiskerHighS}s · IQR ${d.iqrS}s",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    "Min ${d.minS}s · Q1 ${d.q1S}s · Median ${d.medianS}s · Q3 ${d.q3S}s · Max ${d.maxS}s",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                    if (d.outliers.isNotEmpty()) {
                        item {
                            Text("Outlier laps", style = MaterialTheme.typography.labelLarge)
                        }
                        items(d.outliers, key = { it.lapNumber }) { o ->
                            Text(
                                "Lap ${o.lapNumber}: ${o.lapTimeS}s",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LapTimeBoxPlot(
    dist: LapTimeDistributionDto,
    whiskerColor: Color,
    boxFill: Color,
    boxOutline: Color,
    medianColor: Color,
    outlierColor: Color,
    modifier: Modifier = Modifier,
) {
    val pad =
        max(
            (dist.maxS - dist.minS) * 0.08,
            0.05,
        )
    val tMin = dist.minS - pad
    val tMax = dist.maxS + pad
    val span = (tMax - tMin).takeIf { it > 1e-9 } ?: 1.0

    fun xFor(time: Double, w: Float): Float =
        (((time - tMin) / span).toFloat().coerceIn(0f, 1f)) * w

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(132.dp),
    ) {
        val w = size.width
        val h = size.height
        val cy = h / 2f
        val boxHalf = (h * 0.22f).coerceAtMost(28.dp.toPx())

        fun drawWhiskerLine(from: Double, to: Double) {
            drawLine(
                color = whiskerColor,
                strokeWidth = 3f,
                start = Offset(xFor(from, w), cy),
                end = Offset(xFor(to, w), cy),
            )
        }

        drawWhiskerLine(dist.whiskerLowS, dist.whiskerHighS)

        val xQ1 = xFor(dist.q1S, w)
        val xQ3 = xFor(dist.q3S, w)
        val boxLeft = min(xQ1, xQ3)
        val boxWidth = abs(xQ3 - xQ1).coerceAtLeast(6f)
        drawRect(
            color = boxFill,
            topLeft = Offset(boxLeft, cy - boxHalf),
            size = Size(boxWidth, boxHalf * 2),
        )
        drawRect(
            color = boxOutline,
            topLeft = Offset(boxLeft, cy - boxHalf),
            size = Size(boxWidth, boxHalf * 2),
            style = Stroke(width = 2f),
        )

        val mx = xFor(dist.medianS, w)
        drawLine(
            color = medianColor,
            strokeWidth = 4f,
            start = Offset(mx, cy - boxHalf),
            end = Offset(mx, cy + boxHalf),
        )

        dist.outliers.forEach { o ->
            val ox = xFor(o.lapTimeS, w)
            drawCircle(
                color = outlierColor,
                radius = 5.dp.toPx(),
                center = Offset(ox, cy),
            )
        }
    }
}
