package com.pitwall.app.hotpath

import com.pitwall.app.data.TelemetryFrame
import kotlin.math.abs

/**
 * Pedagogical vectors — Ross Bentley's Speed Secrets curriculum encoded as
 * telemetry-triggered coaching moments.
 *
 * Each vector specifies:
 *   - A telemetry predicate (when to fire)
 *   - The coaching concept name
 *   - Level-appropriate instructions
 *   - Priority (1=strategy, 2=technique, 3=safety)
 */
data class PedagogicalVector(
    val id: String,
    val concept: String,
    val instruction: String,
    val priority: Int,
    val predicate: (TelemetryFrame) -> Boolean,
) {
    fun matches(frame: TelemetryFrame) = predicate(frame)
}

object PedagogicalVectors {

    val all = listOf(

        PedagogicalVector(
            id = "threshold_braking",
            concept = "Threshold Braking",
            instruction = "Maximum deceleration — brake hard and hold, load transfers to front tires",
            priority = 2,
        ) { f ->
            f.gLong.value < -0.8f && f.brake.value > 50f && f.brake.confidence > 0.70f
        },

        PedagogicalVector(
            id = "trail_braking",
            concept = "Trail Braking",
            instruction = "Maintain brake pressure into the corner — releases weight to front, increases front grip",
            priority = 2,
        ) { f ->
            f.brake.value > 10f && abs(f.gLat.value) > 0.4f && f.brake.confidence > 0.70f
        },

        PedagogicalVector(
            id = "commitment",
            concept = "Commitment",
            instruction = "Trust the grip circle — you have more grip than you think",
            priority = 2,
        ) { f ->
            abs(f.gLat.value) > 1.0f && f.throttle.value < 20f && f.gLat.confidence > 0.80f
        },

        PedagogicalVector(
            id = "oversteer",
            concept = "Oversteer",
            instruction = "Look where you want to go — ease throttle, modulate",
            priority = 3,
        ) { f ->
            // Proxy: high combo G + large steering correction + past apex
            f.comboG.value > TelemetryFrame.MAX_COMBO_G * 1.0f &&
                    abs(f.steering.value) > 30f && f.pastApex
        },

        PedagogicalVector(
            id = "understeer",
            concept = "Understeer",
            instruction = "Ease throttle, straighten wheel slightly — front tires are saturated",
            priority = 3,
        ) { f ->
            abs(f.gLat.value) < 0.5f && abs(f.steering.value) > 30f &&
                    f.throttle.value > 30f && f.gLat.confidence > 0.80f
        },

        PedagogicalVector(
            id = "coasting",
            concept = "Coasting — Wasted Time",
            instruction = "No brake, no throttle — you're wasting time. Brake or accelerate.",
            priority = 2,
        ) { f ->
            f.isCoasting && f.speed.value > 15f && !f.inCorner && f.cornerProximity > 100f
        },

        PedagogicalVector(
            id = "late_apex",
            concept = "Late Apex",
            instruction = "Wait for turn-in — you can accelerate earlier from a late apex",
            priority = 1,
        ) { f ->
            f.inCorner && !f.pastApex && f.throttle.value < 10f && f.brake.value < 5f
        },

        PedagogicalVector(
            id = "exit_speed",
            concept = "Exit Speed",
            instruction = "Speed on the straight matters more than speed in the corner — get on throttle",
            priority = 2,
        ) { f ->
            f.pastApex && f.throttle.value < 50f && abs(f.gLat.value) < 0.6f &&
                    f.throttle.confidence > 0.70f
        },
    )

    /** Filter vectors that match a frame AND are appropriate for the confidence levels. */
    fun matching(frame: TelemetryFrame): List<PedagogicalVector> {
        // Confidence pre-filter (from ADR-001 / coaching-engine.md)
        if (frame.speed.confidence < 0.50f) return emptyList()
        return all.filter { it.matches(frame) }
    }
}
