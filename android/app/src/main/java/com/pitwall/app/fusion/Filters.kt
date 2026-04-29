package com.pitwall.app.fusion

import kotlin.math.*

/**
 * 1-D Kalman filter for fusing Racelogic GPS speed and OBDLink CAN speed.
 * Weights: Racelogic 0.7, OBDLink 0.3 (GPS is the primary reference).
 *
 * State: [speed]
 * Measurement: [speed from sensor]
 */
class KalmanFilter(
    private var processNoise: Float = 0.01f,    // Q — how much we expect the state to change
    private var measurementNoise: Float = 0.1f, // R — sensor noise variance
) {
    private var x: Float = 0f       // state estimate
    private var p: Float = 1f       // error covariance

    fun update(measurement: Float): Float {
        // Predict
        val pPred = p + processNoise

        // Update
        val k = pPred / (pPred + measurementNoise)  // Kalman gain
        x = x + k * (measurement - x)
        p = (1 - k) * pPred

        return x
    }

    fun reset(initial: Float = 0f) {
        x = initial
        p = 1f
    }
}

/**
 * Fuse two speed measurements with fixed weights.
 * Primary: Racelogic GPS (weight 0.7), Secondary: OBDLink CAN (weight 0.3).
 */
class WeightedSpeedFusion(
    private val primaryWeight: Float = 0.7f,
    private val secondaryWeight: Float = 0.3f,
) {
    private val kalman = KalmanFilter()

    fun update(primary: Float?, secondary: Float?): Float {
        val measurement = when {
            primary != null && secondary != null ->
                primary * primaryWeight + secondary * secondaryWeight
            primary != null -> primary
            secondary != null -> secondary
            else -> return kalman.update(0f)
        }
        return kalman.update(measurement)
    }
}

/**
 * 2nd-order Butterworth low-pass filter for G-force smoothing.
 * Cutoff: 12Hz — removes road surface vibration while preserving
 * real driving dynamics (which peak at ~5Hz for aggressive cornering).
 *
 * Implemented as a biquad IIR filter (direct form II transposed).
 */
class ButterworthFilter(
    sampleRateHz: Float = 10f,
    cutoffHz: Float = 12f,
) {
    private val b0: Float
    private val b1: Float
    private val b2: Float
    private val a1: Float
    private val a2: Float

    private var z1 = 0f
    private var z2 = 0f

    init {
        // Bilinear transform coefficients for 2nd-order Butterworth LPF
        // Pre-warped cutoff frequency
        val wc = (2f * Math.PI.toFloat() * cutoffHz / sampleRateHz)
            .let { 2f * tan(it / 2f) }

        val k = wc / sqrt(2f)
        val denom = 1f + k * sqrt(2f) + k * k

        b0 = k * k / denom
        b1 = 2f * k * k / denom
        b2 = k * k / denom
        a1 = (2f * (k * k - 1f)) / denom
        a2 = (1f - k * sqrt(2f) + k * k) / denom
    }

    fun update(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun reset() {
        z1 = 0f
        z2 = 0f
    }
}

/**
 * Complementary filter for GPS position + IMU dead-reckoning.
 * GPS corrects slow drift; IMU fills the 100ms gaps between GPS fixes.
 *
 * Produces a smooth 50Hz position estimate between 10Hz GPS updates.
 */
class ComplementaryFilter(
    private val alpha: Float = 0.98f,   // weight for IMU (high-freq), 1-alpha for GPS (low-freq)
) {
    private var estimate: Float = 0f
    private var lastGps: Float = 0f
    private var velocity: Float = 0f

    /**
     * Update with a new GPS fix (10Hz).
     * Corrects the accumulated IMU drift.
     */
    fun updateGps(gpsValue: Float) {
        lastGps = gpsValue
        // Blend: GPS correction nudges the estimate back toward ground truth
        estimate = alpha * estimate + (1f - alpha) * gpsValue
    }

    /**
     * Update via IMU dead-reckoning between GPS fixes (10–50Hz).
     * @param accel acceleration in m/s² along the integration axis
     * @param dtS time delta in seconds
     */
    fun updateImu(accel: Float, dtS: Float): Float {
        velocity += accel * dtS
        estimate += velocity * dtS
        return estimate
    }

    fun reset(initial: Float = 0f) {
        estimate = initial
        lastGps = initial
        velocity = 0f
    }

    val current get() = estimate
}
