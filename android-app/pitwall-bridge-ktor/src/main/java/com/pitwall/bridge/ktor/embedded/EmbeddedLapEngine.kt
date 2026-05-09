package com.pitwall.bridge.ktor.embedded

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.round

/**
 * Ports `pitwall.helpers.detect_laps` + `lap_sectors` for Sonoma cumulative-distance / wrap / S/F GPS strategies.
 */
internal object EmbeddedLapEngine {

    const val TRACK_LENGTH_M: Double = 4258.0
    private const val LAP_MIN_S = 60.0
    private const val LAP_MAX_S = 300.0

    private const val SF_LAT = 38.16152
    private const val SF_LON = -122.45472
    private const val SF_HEADING_DEG = 354.2

    /** Matches `sonoma.SECTORS` distance bounds (m along lap). */
    val SONOMA_SECTORS: List<Triple<String, Double, Double>> = listOf(
        Triple("Front Loop", 0.0, 1294.0),
        Triple("Carousel & Back", 1294.0, 2752.0),
        Triple("T10 to Calamity", 2752.0, 4258.0),
    )

    data class TelemetrySample(
        val timestamp: Double,
        val distanceM: Double?,
        val lat: Double?,
        val lon: Double?,
    )

    data class DetectedLap(
        val lapNumber: Int,
        val tStart: Double,
        val tEnd: Double,
        val lapTimeS: Double,
        val frameStart: Int,
        val frameEnd: Int,
    )

    data class SectorSplit(
        val name: String,
        val startM: Double,
        val endM: Double,
        val timeS: Double,
    )

    /**
     * Run detection strategies (same order as Python `detect_laps`).
     */
    fun detectLaps(samples: List<TelemetrySample>): List<DetectedLap> {
        if (samples.size < 10) return emptyList()
        val finalD = samples.asReversed().firstOrNull { it.distanceM != null }?.distanceM ?: 0.0
        val laps = when {
            finalD > TRACK_LENGTH_M * 1.5 -> lapsViaCumulativeDistance(samples, TRACK_LENGTH_M)
            else -> lapsViaDistanceWrap(samples, TRACK_LENGTH_M)
        }.toMutableList()
        if (laps.isEmpty()) {
            laps.addAll(lapsViaGpsCrossing(samples))
        }
        val accepted = laps.filter { it.lapTimeS in LAP_MIN_S..LAP_MAX_S }
        return accepted.mapIndexed { idx, lap ->
            lap.copy(lapNumber = idx + 1)
        }
    }

    /** Sector times for one contiguous lap sample slice (ordered by time). */
    fun lapSectorSplits(lapSamples: List<TelemetrySample>, trackLen: Double = TRACK_LENGTH_M): List<SectorSplit> {
        if (lapSamples.isEmpty()) return emptyList()
        val rows = lapSamples.map { it.timestamp to it.distanceM }
        val baseD = rows.first().second ?: 0.0
        val out = ArrayList<SectorSplit>()
        for ((name, startM, endM) in SONOMA_SECTORS) {
            var tEnter: Double? = null
            var tExit: Double? = null
            for ((t, d) in rows) {
                val p = lapProgress(d, baseD, trackLen) ?: continue
                if (tEnter == null && p >= startM) tEnter = t
                if (tExit == null && p >= endM) {
                    tExit = t
                    break
                }
            }
            if (tEnter == null) continue
            if (tExit == null) tExit = rows.last().first
            val dt = tExit!! - tEnter!!
            if (dt >= 0) {
                out.add(SectorSplit(name = name, startM = startM, endM = endM, timeS = dt))
            }
        }
        return out
    }

    fun sectorSplitsToS123(splits: List<SectorSplit>): Triple<Double, Double, Double> {
        fun t(n: String) = splits.find { it.name == n }?.timeS ?: 0.0
        return Triple(
            t("Front Loop"),
            t("Carousel & Back"),
            t("T10 to Calamity"),
        )
    }

    private fun lapProgress(d: Double?, baseD: Double, trackLen: Double): Double? {
        if (d == null) return null
        var delta = d - baseD
        if (delta < -trackLen / 2) delta += trackLen
        return delta
    }

    private fun lapsViaCumulativeDistance(rows: List<TelemetrySample>, trackLen: Double): List<DetectedLap> {
        val laps = ArrayList<DetectedLap>()
        var startIdx: Int? = null
        for (i in 1 until rows.size) {
            val prevD = rows[i - 1].distanceM ?: continue
            val currD = rows[i].distanceM ?: continue
            if (floor(currD / trackLen) > floor(prevD / trackLen)) {
                val si = startIdx
                if (si != null) {
                    val tStart = rows[si].timestamp
                    val tEnd = rows[i].timestamp
                    laps.add(
                        DetectedLap(
                            lapNumber = 0,
                            tStart = tStart,
                            tEnd = tEnd,
                            lapTimeS = tEnd - tStart,
                            frameStart = si,
                            frameEnd = i,
                        ),
                    )
                }
                startIdx = i
            }
        }
        return laps
    }

    private fun lapsViaDistanceWrap(rows: List<TelemetrySample>, trackLen: Double): List<DetectedLap> {
        val threshold = trackLen / 2
        val laps = ArrayList<DetectedLap>()
        var startIdx = 0
        for (i in 1 until rows.size) {
            val prevD = rows[i - 1].distanceM ?: 0.0
            val currD = rows[i].distanceM ?: 0.0
            if (prevD - currD > threshold) {
                val tStart = rows[startIdx].timestamp
                val tEnd = rows[i - 1].timestamp
                laps.add(
                    DetectedLap(
                        lapNumber = 0,
                        tStart = tStart,
                        tEnd = tEnd,
                        lapTimeS = tEnd - tStart,
                        frameStart = startIdx,
                        frameEnd = i - 1,
                    ),
                )
                startIdx = i
            }
        }
        return laps
    }

    private fun lapsViaGpsCrossing(rows: List<TelemetrySample>): List<DetectedLap> {
        val R = 111320.0
        val cosLat = cos(SF_LAT * PI / 180.0)
        val theta = SF_HEADING_DEG * PI / 180.0
        val sinT = sin(theta)
        val cosT = cos(theta)
        val radialTol = 50.0
        val laps = ArrayList<DetectedLap>()
        var startIdx: Int? = null
        var prevSigned: Double? = null
        for (i in rows.indices) {
            val lat = rows[i].lat
            val lon = rows[i].lon
            if (lat == null || lon == null) continue
            val x = (lon - SF_LON) * cosLat * R
            val y = (lat - SF_LAT) * R
            val signed = -x * sinT + y * cosT
            val radial = hypot(x, y)
            val ps = prevSigned
            if (ps != null && ps < 0 && signed >= 0 && radial < radialTol) {
                val si = startIdx
                if (si != null) {
                    val tStart = rows[si].timestamp
                    val tEnd = rows[i].timestamp
                    laps.add(
                        DetectedLap(
                            lapNumber = 0,
                            tStart = tStart,
                            tEnd = tEnd,
                            lapTimeS = tEnd - tStart,
                            frameStart = si,
                            frameEnd = i,
                        ),
                    )
                }
                startIdx = i
            }
            prevSigned = signed
        }
        return laps
    }
}

internal fun Double.round3(): Double = round(this * 1000.0) / 1000.0
