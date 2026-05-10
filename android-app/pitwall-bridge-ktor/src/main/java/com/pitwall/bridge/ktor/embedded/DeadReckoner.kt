package com.pitwall.bridge.ktor.embedded

import kotlin.math.max

/**
 * Kotlin port of [`dead_reckoning.py`](../../../../../src/pitwall/dead_reckoning.py):
 * 2-state Kalman filter on `(distance_m, speed_ms)` with IMU / CAN speed / GPS distance updates.
 */
data class DeadReckonerConfig(
    val sigmaA: Double = 2.0,
    val sigmaSpeed: Double = 0.3,
    val sigmaGps: Double = 3.0,
)

class DeadReckoner(private val cfg: DeadReckonerConfig = DeadReckonerConfig()) {

    private companion object {
        const val G = 9.81
        const val COLD_VAR = 100.0
    }

    private var x0 = 0.0
    private var x1 = 0.0
    private var p00 = 1000.0
    private var p01 = 0.0
    private var p10 = 0.0
    private var p11 = 1000.0

    var timeSec: Double? = null
        private set
    private var lastA = 0.0
    var nUpdates = 0
        private set

    val distanceM: Double get() = x0
    val speedMs: Double get() = x1

    fun updateImu(t: Double, gLong: Double) {
        val a = gLong * G
        lastA = a
        predictToInternal(t, a)
    }

    fun predictTo(t: Double) {
        predictToInternal(t, lastA)
    }

    private fun predictToInternal(t: Double, a: Double) {
        val tPrev = timeSec
        if (tPrev == null) {
            timeSec = t
            return
        }
        val dt = t - tPrev
        if (dt <= 0) return

        val x0New = x0 + dt * x1 + 0.5 * dt * dt * a
        val x1New = x1 + dt * a

        // P = F P Fᵀ + Q, F = [[1,dt],[0,1]]
        val fp00 = p00 + dt * p10
        val fp01 = p01 + dt * p11
        val fp10 = p10
        val fp11 = p11

        val sa2 = cfg.sigmaA * cfg.sigmaA
        val q00 = 0.25 * dt * dt * dt * dt * sa2
        val q01 = 0.5 * dt * dt * dt * sa2
        val q10 = q01
        val q11 = dt * dt * sa2

        p00 = fp00 + fp01 * dt + q00
        p01 = fp01 + q01
        p10 = fp10 + fp11 * dt + q10
        p11 = fp11 + q11

        x0 = x0New
        x1 = x1New
        timeSec = t
    }

    fun updateSpeed(t: Double, speedMsMeas: Double) {
        if (timeSec == null || p11 >= COLD_VAR) {
            x1 = speedMsMeas
            p11 = cfg.sigmaSpeed * cfg.sigmaSpeed
            timeSec = t
            nUpdates++
            return
        }
        predictTo(t)
        kalmanUpdate(h0 = 0.0, h1 = 1.0, r = cfg.sigmaSpeed * cfg.sigmaSpeed, z = speedMsMeas)
    }

    fun updateDistance(t: Double, distanceMeas: Double) {
        if (timeSec == null || p00 >= COLD_VAR) {
            x0 = distanceMeas
            p00 = cfg.sigmaGps * cfg.sigmaGps
            timeSec = t
            nUpdates++
            return
        }
        predictTo(t)
        kalmanUpdate(h0 = 1.0, h1 = 0.0, r = cfg.sigmaGps * cfg.sigmaGps, z = distanceMeas)
    }

    private fun kalmanUpdate(h0: Double, h1: Double, r: Double, z: Double) {
        val innovation = z - (h0 * x0 + h1 * x1)
        val s = h0 * (h0 * p00 + h1 * p10) + h1 * (h0 * p01 + h1 * p11) + r
        if (s <= 0) return
        val k0 = (p00 * h0 + p01 * h1) / s
        val k1 = (p10 * h0 + p11 * h1) / s

        x0 += k0 * innovation
        x1 += k1 * innovation

        val kh00 = k0 * h0
        val kh01 = k0 * h1
        val kh10 = k1 * h0
        val kh11 = k1 * h1

        val ikh00 = 1.0 - kh00
        val ikh01 = -kh01
        val ikh10 = -kh10
        val ikh11 = 1.0 - kh11

        val np00 = ikh00 * p00 + ikh01 * p10
        val np01 = ikh00 * p01 + ikh01 * p11
        val np10 = ikh10 * p00 + ikh11 * p10
        val np11 = ikh10 * p01 + ikh11 * p11

        p00 = np00
        p01 = np01
        p10 = np10
        p11 = np11
        nUpdates++
    }

    fun reset() {
        x0 = 0.0
        x1 = 0.0
        p00 = 1000.0
        p01 = 0.0
        p10 = 0.0
        p11 = 1000.0
        timeSec = null
        lastA = 0.0
        nUpdates = 0
    }

    fun seed(distanceM: Double, speedMs: Double, t: Double) {
        x0 = distanceM
        x1 = speedMs
        p00 = 1.0
        p01 = 0.0
        p10 = 0.0
        p11 = 0.5 * 0.5
        timeSec = t
    }
}
