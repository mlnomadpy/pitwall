package com.pitwall.bridge.ktor.embedded

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Ports [`can_reader.py`](../../../../../src/pitwall/features/telemetry/can_reader.py)
 * `_consume` behaviour: AIM signed-slot recovery, canonical wide mappings, tall-store pairs,
 * and simplified distance integration from **speed_ms** (full Kalman DR deferred).
 */
class AimMxPipeline {

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

    /** Integrated distance from CAN speed (Termux path also fuses GPS via DeadReckoner). */
    private var distanceM = 0.0
    private var lastWallSec = -1.0
    private var lastFlushedDistance = -1.0

    var seenAny = false
        private set

    /**
     * Process one decoded DBC dict (physical units). Updates wide buffer, returns tall rows
     * `(signal_name, timestamp, value)` matching Flask `_sink_tall` naming.
     */
    fun consumeDecoded(decoded: Map<String, Double>, timestampSec: Double): List<Triple<String, Double, Double>> {
        if (lastWallSec < 0) lastWallSec = timestampSec
        val dt = (timestampSec - lastWallSec).coerceAtLeast(0.0)
        lastWallSec = timestampSec

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

        if (dt > 0 && speedMs > 0) {
            distanceM += speedMs * dt
        }

        return tall
    }

    fun shouldFlushWide(): Boolean {
        if (!seenAny) return false
        if (lastFlushedDistance < 0) return true
        return abs(distanceM - lastFlushedDistance) >= 0.001
    }

    fun markWideFlushed() {
        lastFlushedDistance = distanceM
    }

    fun flushWideRow(timestampSec: Double): EmbeddedDuckDb.FrameRow {
        val combo = hypot(gLat, gLong)
        return EmbeddedDuckDb.FrameRow(
            timestamp = timestampSec,
            distanceM = distanceM,
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
}
