package com.pitwall.paddock.data

import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.model.LatLng
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mock data for the “historical track map” analysis view (speed gradient, elevation, DRS, T labels).
 * Replace with API payloads when Taha’s track bundle is available.
 */
object TrackAnalysisMock {
    const val trackLengthKm: Float = 3.86f
    const val elevationRangeM: Float = 16.2f

    /** Per-segment speed (km/h) along [MockSonomaData.trackPolyline] edges. */
    fun segmentSpeedsKmh(poly: List<LatLng>): List<Float> {
        if (poly.size < 2) return emptyList()
        val n = poly.size - 1
        return List(n) { i ->
            val t = i / n.toFloat()
            75f + t * (280f - 75f) + (if (i % 3 == 0) -15f else 0f) // mock variation
        }
    }

    fun speedToColor(speedKmh: Float, min: Float = 70f, max: Float = 300f): Color {
        val t = ((speedKmh - min) / (max - min)).coerceIn(0f, 1f)
        // Low = red/orange, high = cyan/blue (dashboard-style)
        return Color(
            red = 0.9f * (1f - t) + 0.1f * t,
            green = 0.2f * t,
            blue = 0.3f + 0.65f * t,
            alpha = 1f,
        )
    }

    /** Elevation (m) vs distance (km) for chart. */
    fun elevationProfile(sampleCount: Int = 48): List<Pair<Float, Float>> {
        return List(sampleCount) { i ->
            val d = (i / (sampleCount - 1f)) * trackLengthKm
            val e = 2f + elevationRangeM * 0.5f * (1f + sin(d * 1.8f)) * 0.5f + 0.3f * cos(d * 3.1f)
            d to e
        }
    }

    /** Turn label index 1..n along polyline (for markers) — even spacing. */
    fun turnLabelPositions(poly: List<LatLng>, count: Int = 11): List<Pair<Int, LatLng>> {
        if (poly.isEmpty()) return emptyList()
        val open = if (poly.size > 1 && poly.first() == poly.last()) poly.dropLast(1) else poly
        if (open.isEmpty()) return emptyList()
        if (open.size == 1) return listOf(1 to open.first())
        return (1..count).map { k ->
            val idx = ((k - 1) * (open.size - 1) / (count - 1).coerceAtLeast(1)).coerceIn(0, open.size - 1)
            k to open[idx]
        }
    }

    /** DRS “zones” as polylines (2 segments) — mock positions on loop. */
    fun drsSegmentPolylines(main: List<LatLng>): List<List<LatLng>> {
        if (main.size < 2) return emptyList()
        val mid = main.size / 2
        return listOf(
            listOf(main[0], main[1]),
            listOf(main[mid], main[minOf(mid + 1, main.size - 1)]),
        )
    }

    /** Mock % of lap: low / mid / high speed buckets. */
    fun speedDistribution(): Triple<Float, Float, Float> = Triple(15.4f, 19.4f, 65.2f)

}
