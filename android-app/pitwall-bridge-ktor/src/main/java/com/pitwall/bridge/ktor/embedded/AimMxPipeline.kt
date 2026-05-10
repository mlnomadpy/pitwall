package com.pitwall.bridge.ktor.embedded

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Ports [`can_reader.py`](../../../../../src/pitwall/features/telemetry/can_reader.py)
 * `_consume`: AIM mappings + tall sink + [**DeadReckoner**](DeadReckoner.kt) for `distance_m`
 * (matches Python Kalman fusion).
 */
class AimMxPipeline(private val dr: DeadReckoner = DeadReckoner()) {

    private val signedRecovery =
        setOf(
            "roll_rate_degs",
            "pitch_rate_degs",
            "yaw_rate_degs",
            "lateral_accel_g",
            "inline_accel_g",
            "vertical_accel_g",
            "steer_angle_deg",
        )

    private val canonicalMapping =
        mapOf(
            "speed_mph" to "speed_ms",
            "lateral_accel_g" to "g_lat",
            "inline_accel_g" to "g_long",
            "brake_press_psi" to "brake_bar",
            "throttle_pos_pct" to "throttle_pct",
            "steer_angle_deg" to "steering_deg",
            "gps_lat" to "lat",
            "gps_lon" to "lon",
        )

    private val conversionsBySource =
        mapOf<String, (Double) -> Double>(
            "speed_mph" to { v -> v * 0.44704 },
            "brake_press_psi" to { v -> v * 0.0689476 },
        )

    private val wideFields =
        setOf(
            "distance_m",
            "speed_ms",
            "g_lat",
            "g_long",
            "combo_g",
            "brake_bar",
            "throttle_pct",
            "steering_deg",
            "rpm",
            "lat",
            "lon",
        )

    private var rpm = 0.0
    private var throttle = 0.0
    private var speedMs = 0.0
    private var steer = 0.0
    private var brake = 0.0
    private var gLat = 0.0
    private var gLong = 0.0
    private var lat = 0.0
    private var lon = 0.0

    private var lastFlushedDistance = -1.0

    var seenAny = false
        private set

    fun deadReckoner(): DeadReckoner = dr

    /**
     * Process one decoded DBC dict (physical units). Updates wide buffer, returns tall rows.
     */
    fun consumeDecoded(decoded: Map<String, Double>, timestampSec: Double): List<Triple<String, Double, Double>> {
        val tall = ArrayList<Triple<String, Double, Double>>()
        val wideUpdates = mutableMapOf<String, Double>()

        for ((rawName, rawVal) in decoded) {
            var v = rawVal
            if (rawName in signedRecovery && v > 32767) {
                v =
                    if ("accel" in rawName) {
                        v - 655.36
                    } else {
                        v - 6553.6
                    }
            }

            val targetName = canonicalMapping[rawName] ?: rawName
            if (conversionsBySource.containsKey(rawName)) {
                v = conversionsBySource.getValue(rawName)(v)
            }

            if (targetName in wideFields && targetName != "combo_g") {
                wideUpdates[targetName] = v
            }

            tall.add(Triple(rawName, timestampSec, v))
            if (targetName != rawName) {
                tall.add(Triple(targetName, timestampSec, v))
            }
        }

        if (wideUpdates.isNotEmpty()) {
            seenAny = true
            wideUpdates["rpm"]?.let { rpm = it }
            wideUpdates["throttle_pct"]?.let { throttle = it.coerceIn(0.0, 100.0) }
            wideUpdates["speed_ms"]?.let { speedMs = it }
            wideUpdates["steering_deg"]?.let { steer = it }
            wideUpdates["brake_bar"]?.let { brake = it }
            wideUpdates["g_lat"]?.let { gLat = it }
            wideUpdates["g_long"]?.let { gLong = it }
            wideUpdates["lat"]?.let { lat = it }
            wideUpdates["lon"]?.let { lon = it }
        }

        var advanced = false
        if (wideUpdates.containsKey("g_long")) {
            dr.updateImu(timestampSec, wideUpdates["g_long"]!!)
            advanced = true
        }
        if (wideUpdates.containsKey("speed_ms")) {
            dr.updateSpeed(timestampSec, wideUpdates["speed_ms"]!!)
            advanced = true
        }
        if (wideUpdates.containsKey("distance_m")) {
            val rawD = wideUpdates["distance_m"]!!
            dr.updateDistance(timestampSec, rawD)
            tall.add(Triple("gps_distance_m", timestampSec, rawD))
            advanced = true
        } else if (advanced && dr.timeSec != null) {
            dr.predictTo(timestampSec)
        }

        return tall
    }

    fun shouldFlushWide(): Boolean {
        if (!seenAny) return false
        val d = dr.distanceM
        if (lastFlushedDistance < 0) return true
        return abs(d - lastFlushedDistance) >= 0.001
    }

    fun markWideFlushed() {
        lastFlushedDistance = dr.distanceM
    }

    fun flushWideRow(timestampSec: Double): EmbeddedDuckDb.FrameRow {
        val combo = hypot(gLat, gLong)
        return EmbeddedDuckDb.FrameRow(
            timestamp = timestampSec,
            distanceM = dr.distanceM,
            speedMs = speedMs,
            gLat = gLat,
            gLong = gLong,
            comboG = combo,
            brakeBar = brake,
            throttlePct = throttle,
            steeringDeg = steer,
            rpm = rpm,
            lat = lat,
            lon = lon,
        )
    }

    fun resetSession() {
        dr.reset()
        lastFlushedDistance = -1.0
        seenAny = false
        rpm = 0.0
        throttle = 0.0
        speedMs = 0.0
        steer = 0.0
        brake = 0.0
        gLat = 0.0
        gLong = 0.0
        lat = 0.0
        lon = 0.0
    }
}
