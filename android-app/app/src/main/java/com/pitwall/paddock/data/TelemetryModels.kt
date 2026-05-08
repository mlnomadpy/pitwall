package com.pitwall.paddock.data

import kotlinx.serialization.Serializable

// ═══════════════════════════════════════════════════════════════════════════════
// Telemetry data models — ported from android/app/src/main/java/com/pitwall/app/data/
// Package-renamed, Flutter toChannelMap() stripped, logic unchanged.
// ═══════════════════════════════════════════════════════════════════════════════

// ── Signal value ──────────────────────────────────────────────────────────────

/**
 * A signal value with confidence metadata (ADR-001).
 * Every sensor signal carries this wrapper.
 */
data class SignalValue(
    val value: Float,
    val confidence: Float,      // 0.0–1.0
    val source: String,         // "racelogic:gps", "racelogic:imu", "obdlink:can", "fused:kalman"
    val hz: Float,              // actual update rate
    val stale: Boolean = false, // no update in >2× expected period
) {
    companion object {
        val UNKNOWN = SignalValue(0f, 0f, "unknown", 0f, stale = true)
        fun racelogicGps(value: Float, confidence: Float = 0.95f) = SignalValue(value, confidence, "racelogic:gps", 10f)
        fun racelogicImu(value: Float, confidence: Float = 0.95f) = SignalValue(value, confidence, "racelogic:imu", 10f)
        fun obdlinkCan(value: Float,   confidence: Float = 0.95f) = SignalValue(value, confidence, "obdlink:can", 10f)
        fun fused(value: Float, source: String = "fused:kalman", confidence: Float = 0.95f) = SignalValue(value, confidence, source, 50f)
    }
}

// ── Telemetry frame ───────────────────────────────────────────────────────────

data class LegacyTelemetryFrame(
    val timestamp: Double,
    val sources: List<String>,

    val latitude: SignalValue,
    val longitude: SignalValue,
    val speed: SignalValue,     // m/s
    val heading: SignalValue,

    val gLat: SignalValue,      // lateral G
    val gLong: SignalValue,     // longitudinal G
    val comboG: SignalValue,    // sqrt(gLat² + gLong²)

    val throttle: SignalValue,  // 0–100%
    val brake: SignalValue,     // bar (0–104)
    val rpm: SignalValue,
    val steering: SignalValue,
    val coolantTemp: SignalValue,
    val oilTemp: SignalValue,

    val distance: SignalValue,
    val cornerProximity: Float,
    val currentCorner: String?,
    val pastApex: Boolean,
    val sector: String?,
    val lap: Int,
    val lapTime: Float,
    val completedLapTime: Float? = null,
    val gear: Int,
) {
    val speedKmh    get() = speed.value * 3.6f
    val speedMph    get() = speed.value * 2.237f
    val inCorner    get() = currentCorner != null
    val gripUsage   get() = comboG.value / MAX_COMBO_G
    val isCoasting  get() = throttle.value < 5f && brake.value < 2f

    companion object {
        const val MAX_COMBO_G    = 2.29f
        const val MAX_BRAKE_BAR  = 73.5f
        const val MAX_RPM        = 8321f

        val GEAR_RATIOS = mapOf(1 to 13.17f, 2 to 8.09f, 3 to 5.77f, 4 to 4.52f, 5 to 3.68f, 6 to 3.09f)

        fun deriveGear(speedMs: Float, rpm: Float): Int {
            if (speedMs < 2f || rpm < 500f) return 0
            val wheelCircumference = 2 * Math.PI.toFloat() * 0.315f
            val ratio = rpm / (speedMs * 60f / wheelCircumference)
            return GEAR_RATIOS.minByOrNull { (_, r) -> Math.abs(r - ratio) }?.key ?: 0
        }
    }
}

data class RawFrame(
    val timestamp: Double,
    val source: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val speedMs: Float? = null,
    val heading: Float? = null,
    val gLat: Float? = null,
    val gLong: Float? = null,
    val comboG: Float? = null,
    val distance: Float? = null,
    val satellites: Int? = null,
    val throttle: Float? = null,
    val brakePressure: Float? = null,
    val brakePosition: Float? = null,
    val rpm: Float? = null,
    val steering: Float? = null,
    val coolantTemp: Float? = null,
    val oilTemp: Float? = null,
    val oilPressure: Float? = null,
    val fuelLevel: Float? = null,
    val batteryVoltage: Float? = null,
)

// ── Corner session stats ──────────────────────────────────────────────────────

/**
 * Running accumulator for a single corner — internal to service layer.
 */
internal data class CornerAccumulator(
    val name: String,
    var sumEntryKmh: Float = 0f,
    var sumApexKmh: Float  = 0f,
    var sumExitKmh: Float  = 0f,
    var entryFrames: Int   = 0,
    var apexFrames: Int    = 0,
    var exitFrames: Int    = 0,
    var maxG: Float        = 0f,
    var coastFrames: Int   = 0,
    var trailFrames: Int   = 0,
    var totalFrames: Int   = 0,
) {
    fun toStats(corner: TrackCorner): CornerSessionStats = CornerSessionStats(
        name             = name,
        observedEntryKmh = if (entryFrames > 0) sumEntryKmh / entryFrames else 0f,
        observedApexKmh  = if (apexFrames  > 0) sumApexKmh  / apexFrames  else 0f,
        observedExitKmh  = if (exitFrames  > 0) sumExitKmh  / exitFrames  else 0f,
        refEntryKmh      = corner.entrySpeedKmh,
        refApexKmh       = corner.apexSpeedKmh,
        refExitKmh       = corner.exitSpeedKmh,
        peakG            = maxG,
        coastPct         = if (totalFrames > 0) coastFrames * 100f / totalFrames else 0f,
        trailPct         = if (entryFrames > 0) trailFrames * 100f / entryFrames else 0f,
        sampleCount      = totalFrames,
    )
}

/**
 * Per-corner session statistics compared against the Sonoma gold standard.
 * Exposed from ViewModel → PostSessionAnalysisScreen.
 */
data class CornerSessionStats(
    val name: String,
    val observedEntryKmh: Float,
    val observedApexKmh: Float,
    val observedExitKmh: Float,
    val refEntryKmh: Float,
    val refApexKmh: Float,
    val refExitKmh: Float,
    val peakG: Float,
    val coastPct: Float,
    val trailPct: Float,
    val sampleCount: Int,
) {
    val entryDeltaKmh get() = observedEntryKmh - refEntryKmh
    val apexDeltaKmh  get() = observedApexKmh  - refApexKmh
    val exitDeltaKmh  get() = observedExitKmh  - refExitKmh
    val totalDeltaKmh get() = entryDeltaKmh + apexDeltaKmh + exitDeltaKmh
    val hasData       get() = sampleCount > 0
}

// ── Driver insight ────────────────────────────────────────────────────────────

/**
 * A post-session AI coaching insight (GET /insights).
 */
data class DriverInsight(
    val id: String,
    val rank: Int,
    val title: String,
    val detail: String,
    val corners: List<String>,
    val metricLabel: String,
    val metricValue: String,
    val effort: Int,
    val estGainS: Float,
    val evidenceBursts: Int,
    val lap: Int = 0,
) {
    val severity: Severity get() = when (id) {
        "coast_excess"  -> if (estGainS >= 0.8f) Severity.HIGH else Severity.MEDIUM
        "grip_headroom" -> if (estGainS >= 0.7f) Severity.HIGH else Severity.MEDIUM
        "trail_absent"  -> Severity.MEDIUM
        "braking_late"  -> Severity.MEDIUM
        else            -> Severity.LOW
    }
    enum class Severity { HIGH, MEDIUM, LOW }
}

// ── Track map ─────────────────────────────────────────────────────────────────

@Serializable
data class TrackMap(
    val name: String,
    val trackLength: Float,
    val sfLat: Double,
    val sfLon: Double,
    val corners: List<TrackCorner>,
    val sectors: List<TrackSector>,
) {
    private fun modDistance(distance: Float): Float =
        if (trackLength > 0f) distance % trackLength else distance

    fun cornerAt(distance: Float): TrackCorner? {
        val d = modDistance(distance)
        return corners.find { d in it.startDistance..it.endDistance }
    }

    fun nearestCorner(distance: Float): TrackCorner? {
        val d = modDistance(distance)
        return corners.minByOrNull { Math.abs(it.apexDistance - d) }
    }

    fun nextCorner(distance: Float): TrackCorner? {
        val d = modDistance(distance)
        return corners.filter { it.apexDistance >= d }.minByOrNull { it.apexDistance - d }
            ?: corners.firstOrNull()
    }

    fun distanceToCorner(distance: Float, corner: TrackCorner): Float {
        val d    = modDistance(distance)
        var diff = corner.apexDistance - d
        if (diff < -trackLength / 2f) diff += trackLength
        return diff
    }

    fun isPastApex(distance: Float, corner: TrackCorner): Boolean =
        modDistance(distance) > corner.apexDistance

    fun sectorAt(distance: Float): TrackSector? {
        val d = modDistance(distance)
        return sectors.find { d in it.startDistance..it.endDistance }
    }
}

@Serializable
data class TrackCorner(
    val name: String,
    val startDistance: Float,
    val apexDistance: Float,
    val endDistance: Float,
    val direction: String,       // "L" or "R"
    val gear: Int,
    val elevationChange: Float,
    val entrySpeedKmh: Float,
    val apexSpeedKmh: Float,
    val exitSpeedKmh: Float,
    val camber: Float,
)

@Serializable
data class TrackSector(
    val name: String,
    val startDistance: Float,
    val endDistance: Float,
)

/** Sonoma Raceway gold-standard reference data. */
object SonomaGoldStandard {
    val track = TrackMap(
        name        = "Sonoma Raceway",
        trackLength = 3765f,
        sfLat       = 38.1614,
        sfLon       = -122.4549,
        corners = listOf(
            TrackCorner("Turn 1",  150f,  220f,  300f, "L", 2,   0f, 111f, 113f, 117f,   0f),
            TrackCorner("Turn 3",  600f,  700f,  820f, "R", 4,  50f, 104f,  87f, 102f,  11f),
            TrackCorner("Turn 6", 1200f, 1320f, 1440f, "R", 5,  86f,  92f,  77f, 105f, -11f),
            TrackCorner("Turn 9", 2100f, 2200f, 2300f, "L", 3,  66f, 121f, 116f, 132f, -16f),
            TrackCorner("Turn 10",2500f, 2620f, 2760f, "R", 6, 124f, 106f,  73f, 108f,   0f),
            TrackCorner("Turn 11",2900f, 3020f, 3200f, "R", 5, 134f,  88f,  64f,  95f,   0f),
        ),
        sectors = listOf(
            TrackSector("Sector 1",    0f, 1255f),
            TrackSector("Sector 2", 1255f, 2510f),
            TrackSector("Sector 3", 2510f, 3765f),
        ),
    )
}

// ── Track outline (GPS path for mini-map) ─────────────────────────────────────

data class TrackPoint(val x: Float, val y: Float)

data class CornerMarker(
    val name: String,
    val posX: Float,
    val posY: Float,
    val apexDistanceM: Float,
)

/**
 * Full normalised track outline built from sonoma.json reference_line (1065 pts).
 * All coordinates in [0, 1] canvas space.
 */
data class TrackOutline(
    val points: List<TrackPoint>,
    val cornerMarkers: List<CornerMarker>,
    val trackLengthM: Float,
) {
    fun progressToPoint(progress: Float): TrackPoint {
        if (points.isEmpty()) return TrackPoint(0.5f, 0.5f)
        val idx = (progress.coerceIn(0f, 1f) * (points.size - 1)).toInt()
            .coerceIn(0, points.size - 1)
        return points[idx]
    }

    fun distanceToPoint(distanceM: Float): TrackPoint {
        val modDist = distanceM % trackLengthM.coerceAtLeast(1f)
        return progressToPoint(modDist / trackLengthM.coerceAtLeast(1f))
    }

    companion object {
        fun fromJson(json: String): TrackOutline? = try {
            val root        = org.json.JSONObject(json)
            val trackLength = root.optDouble("track_length_m", 4258.0).toFloat()
            val refLine     = root.getJSONArray("reference_line")

            val rawLats = mutableListOf<Double>()
            val rawLons = mutableListOf<Double>()
            for (i in 0 until refLine.length()) {
                val pt = refLine.getJSONObject(i)
                rawLats += pt.getDouble("lat")
                rawLons += pt.getDouble("lon")
            }

            val minLat = rawLats.min(); val maxLat = rawLats.max()
            val minLon = rawLons.min(); val maxLon = rawLons.max()
            val scale  = maxOf((maxLat - minLat).coerceAtLeast(1e-6), (maxLon - minLon).coerceAtLeast(1e-6))

            val points = rawLats.zip(rawLons).map { (lat, lon) ->
                TrackPoint(
                    x = ((lon - minLon) / scale).toFloat(),
                    y = (1f - ((lat - minLat) / scale).toFloat()),
                )
            }

            val cornersArr = root.getJSONArray("corners")
            val markers = (0 until cornersArr.length()).map { i ->
                val c     = cornersArr.getJSONObject(i)
                val apex  = c.getJSONObject("apex")
                CornerMarker(
                    name          = c.getString("name"),
                    posX          = ((apex.getDouble("lon") - minLon) / scale).toFloat(),
                    posY          = (1f - ((apex.getDouble("lat") - minLat) / scale).toFloat()),
                    apexDistanceM = apex.getDouble("distance").toFloat(),
                )
            }

            TrackOutline(points, markers, trackLength)
        } catch (e: Exception) {
            android.util.Log.w("TrackOutline", "Failed to parse: ${e.message}")
            null
        }
    }
}
