package com.pitwall.app.fusion

import com.pitwall.app.data.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Sensor Fusion Engine — merges RawFrames from Racelogic + OBDLink into
 * confidence-annotated TelemetryFrames at 10Hz.
 *
 * Pipeline per frame:
 *   1. Receive RawFrame from either sensor
 *   2. Run Kalman (speed), Butterworth (G-forces), Complementary (position)
 *   3. Annotate each signal with confidence
 *   4. Emit TelemetryFrame to downstream consumers (hot path + Antigravity)
 */
class SensorFusion(private val track: com.pitwall.app.data.TrackMap) {

    // Filters
    private val speedFusion = WeightedSpeedFusion()
    private val gLatFilter = ButterworthFilter(sampleRateHz = 10f, cutoffHz = 12f)
    private val gLongFilter = ButterworthFilter(sampleRateHz = 10f, cutoffHz = 12f)
    private val positionFilter = ComplementaryFilter()

    // Latest raw values from each sensor
    private var latestRacelogic: RawFrame? = null
    private var latestObd: RawFrame? = null

    // Lap tracking
    private var lap = 0
    private var lapStartTime = 0.0
    private var wasNearSF = false
    private var sfCooldown = 0
    private var cumulativeDistance = 0f
    private var lastTimestamp = 0.0

    // Telemetry output — consumed by HotPath, AntigravityPipeline, LocalAnalytics
    private val _frames = MutableSharedFlow<TelemetryFrame>(extraBufferCapacity = 64)
    val frames: SharedFlow<TelemetryFrame> = _frames.asSharedFlow()

    /** Called from Racelogic Bluetooth service on each incoming VBO line. */
    suspend fun onRacelogicFrame(raw: RawFrame) {
        latestRacelogic = raw
        emitFusedFrame()
    }

    /** Called from OBDLink Bluetooth service on each incoming CAN frame. */
    suspend fun onObdFrame(raw: RawFrame) {
        latestObd = raw
        // OBD frames are not the primary tick — Racelogic drives the emission
    }

    private suspend fun emitFusedFrame() {
        val rl = latestRacelogic ?: return
        val obd = latestObd

        val timestamp = rl.timestamp
        val dt = if (lastTimestamp > 0.0) (timestamp - lastTimestamp).toFloat() else 0.1f
        lastTimestamp = timestamp

        // ── Speed ─────────────────────────────────────────────────────────────
        val fusedSpeed = speedFusion.update(rl.speedMs, null)  // OBD speed not mapped
        val speedConf = gpsConfidence(rl.satellites ?: 100)

        // ── G-forces (Butterworth filtered) ───────────────────────────────────
        val filteredGLat = gLatFilter.update(rl.gLat ?: 0f)
        val filteredGLong = gLongFilter.update(rl.gLong ?: 0f)
        val combo = sqrt(filteredGLat * filteredGLat + filteredGLong * filteredGLong)

        // ── Distance ──────────────────────────────────────────────────────────
        if (rl.distance != null) {
            cumulativeDistance = rl.distance
        } else {
            cumulativeDistance += fusedSpeed * dt
        }

        // ── Lap tracking ──────────────────────────────────────────────────────
        val nearSF = rl.lat != null && haversineM(
            rl.lat, rl.lon ?: 0.0, track.sfLat, track.sfLon
        ) < 30
        if (sfCooldown > 0) sfCooldown--

        if (nearSF && !wasNearSF && lap > 0 && sfCooldown == 0) {
            val lapTime = (timestamp - lapStartTime).toFloat()
            if (lapTime in 30f..300f) {
                lap++
                lapStartTime = timestamp
                sfCooldown = 50
            }
        } else if (nearSF && !wasNearSF && lap == 0) {
            lap = 1
            lapStartTime = timestamp
            sfCooldown = 50
        }
        wasNearSF = nearSF

        val lapTime = (timestamp - lapStartTime).toFloat().coerceAtLeast(0f)

        // ── Track position context ─────────────────────────────────────────────
        val corner = track.cornerAt(cumulativeDistance)
        val nearestCorner = track.nearestCorner(cumulativeDistance)
        val distToCorner = nearestCorner?.let { track.distanceToCorner(cumulativeDistance, it) } ?: 999f
        val pastApex = corner != null && nearestCorner != null &&
                track.isPastApex(cumulativeDistance, nearestCorner)
        val sector = track.sectorAt(cumulativeDistance)

        // ── Gear (derived from RPM/speed ratio) ────────────────────────────────
        val gear = TelemetryFrame.deriveGear(fusedSpeed, obd?.rpm ?: 0f)

        // ── Build confidence-annotated frame ──────────────────────────────────
        val frame = TelemetryFrame(
            timestamp = timestamp,
            sources = buildList {
                add("racelogic")
                if (obd != null) add("obdlink")
            },
            latitude = SignalValue.racelogicGps(rl.lat?.toFloat() ?: 0f, speedConf),
            longitude = SignalValue.racelogicGps(rl.lon?.toFloat() ?: 0f, speedConf),
            speed = SignalValue.fused(fusedSpeed, confidence = speedConf),
            heading = SignalValue.racelogicGps(rl.heading ?: 0f, speedConf),
            gLat = SignalValue.racelogicImu(filteredGLat),
            gLong = SignalValue.racelogicImu(filteredGLong),
            comboG = SignalValue.racelogicImu(combo),
            throttle = obd?.throttle?.let { SignalValue.obdlinkCan(it) } ?: SignalValue.UNKNOWN,
            brake = obd?.brakePressure?.let { SignalValue.obdlinkCan(it) } ?: SignalValue.UNKNOWN,
            rpm = obd?.rpm?.let { SignalValue.obdlinkCan(it) } ?: SignalValue.UNKNOWN,
            steering = obd?.steering?.let { SignalValue.obdlinkCan(it.coerceIn(-500f, 500f)) }
                ?: SignalValue.UNKNOWN,
            coolantTemp = obd?.coolantTemp?.let { SignalValue.obdlinkCan(it, 0.90f) }
                ?: SignalValue.UNKNOWN,
            oilTemp = obd?.oilTemp?.let { SignalValue.obdlinkCan(it, 0.90f) }
                ?: SignalValue.UNKNOWN,
            distance = SignalValue.fused(cumulativeDistance, "derived:odometer"),
            cornerProximity = distToCorner,
            currentCorner = corner?.name,
            pastApex = pastApex,
            sector = sector?.name,
            lap = lap,
            lapTime = lapTime,
            gear = gear,
        )

        _frames.emit(frame)
    }

    /** GPS confidence from satellite quality flag (not literal count — VBO encodes quality). */
    private fun gpsConfidence(satellites: Int): Float = when {
        satellites > 60 -> 0.95f
        satellites > 20 -> 0.80f
        satellites > 0  -> 0.50f
        else            -> 0.00f
    }

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val R = 6_371_000.0
        val dlat = Math.toRadians(lat2 - lat1)
        val dlon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dlat / 2).let { it * it } +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dlon / 2).let { it * it }
        return (R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))).toFloat()
    }
}
