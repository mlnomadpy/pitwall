package com.pitwall.app.hotpath

import com.pitwall.app.data.TelemetryFrame
import com.pitwall.app.data.AudioCue
import com.pitwall.app.data.Pattern

/**
 * Rule-based sonic co-driver. Direct port of sonic_model.py.
 *
 * Three layers of continuous sound + triggered events:
 *   Layer 1 — Grip tone: continuous, pitch tracks friction circle usage
 *   Layer 2 — Brake approach: ascending pitch as corner approaches
 *   Layer 3 — Trail brake guide: pitch follows brake release through corner
 *   Layer 4 — Throttle pulse: encourages throttle pickup after apex
 *   Layer 5 — Coast warning: detects wasted time with no pedal input
 */
object SonicModel {

    fun computeCues(frame: TelemetryFrame, prevFrame: TelemetryFrame? = null): List<AudioCue> {
        val cues = mutableListOf<AudioCue>()

        val speed = frame.speed.value
        val gLat = frame.gLat.value
        val comboG = frame.comboG.value
        val brake = frame.brake.value
        val throttle = frame.throttle.value
        val distToCorner = frame.cornerProximity
        val cornerSeverity = frame.inCorner.let { if (it) 3 else 0 }
            .let { _ ->
                // Use the nearest corner's severity from track context
                // Default 3 if in a corner, 0 otherwise — refined by track loader
                if (frame.inCorner) 3 else 0
            }
        val pastApex = frame.pastApex
        val inCorner = frame.inCorner

        // ── Layer 1: Grip Tone (always active) ────────────────────────────────
        val gripUsage = (comboG / TelemetryFrame.MAX_COMBO_G).coerceAtMost(1.5f)

        when {
            gripUsage > 1.05f -> cues += AudioCue(
                layer = "grip",
                frequency = 1600f,
                volume = 0.8f,
                pattern = Pattern.BUZZ,
                priority = 3,
                reason = "Over grip limit: ${comboG}G / ${TelemetryFrame.MAX_COMBO_G}G max",
            )
            gripUsage > 0.90f -> cues += AudioCue(
                layer = "grip",
                frequency = 1200f + (gripUsage - 0.9f) * 4000f,
                volume = 0.5f,
                pattern = Pattern.CONTINUOUS,
                priority = 0,
                reason = "Near limit: ${(gripUsage * 100).toInt()}% grip usage",
            )
            gripUsage > 0.30f -> cues += AudioCue(
                layer = "grip",
                frequency = 200f + gripUsage * 1000f,
                volume = 0.1f + gripUsage * 0.25f,
                pattern = Pattern.CONTINUOUS,
                priority = 0,
                reason = "Grip: ${(gripUsage * 100).toInt()}%",
            )
            // Below 0.3 — silence (on straight, no feedback needed)
        }

        // ── Layer 2: Brake Approach ────────────────────────────────────────────
        if (cornerSeverity > 0 && distToCorner < cornerSeverity * 50 &&
            distToCorner > 5 && brake < 2f
        ) {
            val approachPct = (1f - distToCorner / (cornerSeverity * 50f)).coerceIn(0f, 1f)
            if (approachPct > 0.85f) {
                cues += AudioCue(
                    layer = "brake_approach",
                    frequency = 1200f,
                    volume = 0.7f,
                    pattern = Pattern.FAST_PULSE,
                    priority = 2,
                    reason = "Brake zone! ${distToCorner.toInt()}m to corner",
                )
            } else {
                cues += AudioCue(
                    layer = "brake_approach",
                    frequency = 400f + approachPct * 800f,
                    volume = 0.15f + approachPct * 0.45f,
                    pattern = Pattern.PULSE,
                    priority = 1,
                    reason = "Approaching corner: ${distToCorner.toInt()}m",
                )
            }
        }

        // ── Layer 3: Trail Brake Guide ─────────────────────────────────────────
        if (brake > 3f && kotlin.math.abs(gLat) > 0.4f && inCorner) {
            val brakePct = (brake / TelemetryFrame.MAX_BRAKE_BAR).coerceAtMost(1f)
            cues += AudioCue(
                layer = "trail_brake",
                frequency = 300f + brakePct * 600f,
                volume = 0.35f,
                pattern = Pattern.CONTINUOUS,
                priority = 1,
                reason = "Trail braking: ${brake}bar (${(brakePct * 100).toInt()}%)",
            )
        }

        // ── Layer 4: Throttle Pickup ───────────────────────────────────────────
        if (pastApex && throttle < 20f && kotlin.math.abs(gLat) > 0.3f && speed > 10f) {
            val alreadyPastApex = prevFrame?.pastApex == true
            if (alreadyPastApex) {
                cues += AudioCue(
                    layer = "throttle",
                    frequency = 280f,
                    volume = 0.3f,
                    pattern = Pattern.PULSE,
                    priority = 1,
                    reason = "Past apex, throttle only ${throttle.toInt()}%",
                )
            }
        }

        // ── Layer 5: Coast Warning ─────────────────────────────────────────────
        if (throttle < 5f && brake < 2f && speed > 15f && !inCorner && distToCorner > 100f) {
            cues += AudioCue(
                layer = "coast_warning",
                frequency = 350f,
                volume = 0.25f,
                pattern = Pattern.PULSE,
                priority = 1,
                reason = "Coasting on straight: throttle ${throttle.toInt()}%, brake ${brake}bar",
            )
        }

        return cues
    }

    fun computeLapEstimateCue(
        currentDistance: Float,
        elapsedTime: Float,
        trackLength: Float,
        bestLapTime: Float?,
    ): AudioCue? {
        if (currentDistance < 10f || trackLength < 100f) return null
        val progress = (currentDistance % trackLength) / trackLength
        if (progress < 0.05f) return null

        val predictedLap = elapsedTime / progress

        return when {
            bestLapTime == null -> AudioCue(
                layer = "lap_estimate",
                frequency = 600f,
                volume = 0.4f,
                pattern = Pattern.CHIME_NEUTRAL,
                priority = 0,
                reason = "Predicted lap: ${predictedLap}s (no best)",
            )
            predictedLap < bestLapTime - 0.5f -> AudioCue(
                layer = "lap_estimate",
                frequency = 800f,
                volume = 0.5f,
                pattern = Pattern.CHIME_UP,
                priority = 0,
                reason = "Predicted ${predictedLap}s — ${bestLapTime - predictedLap}s AHEAD",
            )
            predictedLap > bestLapTime + 0.5f -> AudioCue(
                layer = "lap_estimate",
                frequency = 400f,
                volume = 0.5f,
                pattern = Pattern.CHIME_DOWN,
                priority = 0,
                reason = "Predicted ${predictedLap}s — ${predictedLap - bestLapTime}s BEHIND",
            )
            else -> AudioCue(
                layer = "lap_estimate",
                frequency = 600f,
                volume = 0.3f,
                pattern = Pattern.CHIME_NEUTRAL,
                priority = 0,
                reason = "Predicted ${predictedLap}s — even with best",
            )
        }
    }
}
