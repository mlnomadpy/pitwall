package com.pitwall.app.data

/**
 * A normalised (0–1 canvas space) point on the track outline path.
 * Computed once from the GPS reference_line in sonoma.json.
 */
data class TrackPoint(val x: Float, val y: Float)

/**
 * Position of a corner apex on the normalised canvas, plus its distance
 * along the track for driver-position interpolation.
 */
data class CornerMarker(
    val name: String,
    val posX: Float,
    val posY: Float,
    val apexDistanceM: Float,
)

/**
 * Full track outline ready for Canvas drawing, built from sonoma.json's
 * 1065-point reference_line.  All coordinates are normalised to [0, 1].
 *
 * Usage — draw with drawPath using the [points] list, and place a driver
 * dot by linear interpolation on [points] via [progressToPoint].
 */
data class TrackOutline(
    val points: List<TrackPoint>,
    val cornerMarkers: List<CornerMarker>,
    val trackLengthM: Float,
) {
    /**
     * Convert a lap-progress fraction (0–1) to the corresponding canvas point.
     * Uses O(1) index math — fast enough to call every frame.
     */
    fun progressToPoint(progress: Float): TrackPoint {
        if (points.isEmpty()) return TrackPoint(0.5f, 0.5f)
        val idx = (progress.coerceIn(0f, 1f) * (points.size - 1)).toInt()
            .coerceIn(0, points.size - 1)
        return points[idx]
    }

    /**
     * Convert a raw distance (metres) to the corresponding canvas point.
     */
    fun distanceToPoint(distanceM: Float): TrackPoint {
        val modDist = distanceM % trackLengthM.coerceAtLeast(1f)
        return progressToPoint(modDist / trackLengthM.coerceAtLeast(1f))
    }

    companion object {
        /**
         * Build a TrackOutline from the raw JSON string of sonoma.json.
         * Parses the "reference_line" array and normalises lat/lon to [0,1].
         * Also reads "corners" for the CornerMarker list.
         */
        fun fromJson(json: String): TrackOutline? = try {
            val root = org.json.JSONObject(json)
            val trackLength = root.optDouble("track_length_m", 4258.0).toFloat()

            // ── Parse reference_line ─────────────────────────────────────────
            val refLine = root.getJSONArray("reference_line")
            val rawLats = mutableListOf<Double>()
            val rawLons = mutableListOf<Double>()
            for (i in 0 until refLine.length()) {
                val pt = refLine.getJSONObject(i)
                rawLats += pt.getDouble("lat")
                rawLons += pt.getDouble("lon")
            }

            val minLat = rawLats.min()
            val maxLat = rawLats.max()
            val minLon = rawLons.min()
            val maxLon = rawLons.max()
            val latRange = (maxLat - minLat).coerceAtLeast(1e-6)
            val lonRange = (maxLon - minLon).coerceAtLeast(1e-6)

            // Keep aspect ratio: use the larger range as the denominator for both axes
            val scale = maxOf(latRange, lonRange)

            val points = rawLats.zip(rawLons).map { (lat, lon) ->
                // Latitude increases upward → invert Y so north is up on screen
                TrackPoint(
                    x = ((lon - minLon) / scale).toFloat(),
                    y = (1f - ((lat - minLat) / scale).toFloat()),
                )
            }

            // ── Parse corners ────────────────────────────────────────────────
            val cornersArr = root.getJSONArray("corners")
            val markers = mutableListOf<CornerMarker>()
            for (i in 0 until cornersArr.length()) {
                val c = cornersArr.getJSONObject(i)
                val apex = c.getJSONObject("apex")
                val apexDist = apex.getDouble("distance").toFloat()
                val apexLat  = apex.getDouble("lat")
                val apexLon  = apex.getDouble("lon")
                markers += CornerMarker(
                    name = c.getString("name"),
                    posX = ((apexLon - minLon) / scale).toFloat(),
                    posY = (1f - ((apexLat - minLat) / scale).toFloat()),
                    apexDistanceM = apexDist,
                )
            }

            TrackOutline(points, markers, trackLength)
        } catch (e: Exception) {
            android.util.Log.w("TrackOutline", "Failed to parse track outline: ${e.message}")
            null
        }
    }
}
