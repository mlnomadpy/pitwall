package com.pitwall.paddock.data

import androidx.compose.ui.graphics.Color

/**
 * Mock series for F1-style feedback visuals until Taha’s telemetry endpoints are wired.
 */
object AnalysisVisualsMock {

    /** (distance m, value) — 0..~5200m typical lap distance for chart X. */
    fun speedTraceA(sampleCount: Int = 64): List<Pair<Float, Float>> = List(sampleCount) { i ->
        val t = i / (sampleCount - 1f).coerceAtLeast(1f)
        val d = t * 5100f
        val wobble = kotlin.math.sin(t * 22.0) * 35.0
        val base = 140.0 + t * 90.0 + wobble
        d to base.toFloat().coerceIn(50f, 320f)
    }

    fun speedTraceB(sampleCount: Int = 64): List<Pair<Float, Float>> = List(sampleCount) { i ->
        val t = i / (sampleCount - 1f).coerceAtLeast(1f)
        val d = t * 5100f
        val wobble = kotlin.math.sin(t * 20.0 + 0.4) * 38.0
        val base = 135.0 + t * 92.0 + wobble
        d to base.toFloat().coerceIn(50f, 320f)
    }

    fun lapTimeTrend(): List<Pair<Int, Float>> = listOf(
        1 to 99.1f, 2 to 98.8f, 3 to 99.0f, 4 to 98.5f, 5 to 98.3f, 6 to 98.6f, 7 to 98.1f, 8 to 97.9f, 9 to 98.0f, 10 to 97.7f,
    )

    data class DriverSectorDeltas(
        val code: String,
        val teamColor: Color,
        val s1Delta: Float,
        val s2Delta: Float,
        val s3Delta: Float,
    )

    fun sectorDeltasGrid(): List<DriverSectorDeltas> = listOf(
        DriverSectorDeltas("VER", Color(0xFF3671C6), 0.012f, -0.005f, 0.003f),
        DriverSectorDeltas("LEC", Color(0xFFE8002D), -0.008f, 0.021f, -0.001f),
        DriverSectorDeltas("NOR", Color(0xFFFF8700), 0.0f, -0.012f, 0.008f),
        DriverSectorDeltas("RUS", Color(0xFF6CD3BF), 0.004f, 0.0f, -0.009f),
    )

    enum class Compound { Soft, Medium, Hard }

    data class Stint(val laps: Int, val compound: Compound)

    data class DriverStints(val code: String, val stints: List<Stint>)

    fun tireStrategy(): List<DriverStints> = listOf(
        DriverStints("ALB", listOf(Stint(12, Compound.Soft), Stint(22, Compound.Medium), Stint(14, Compound.Hard))),
        DriverStints("NOR", listOf(Stint(10, Compound.Medium), Stint(28, Compound.Medium), Stint(10, Compound.Soft))),
        DriverStints("PIA", listOf(Stint(8, Compound.Soft), Stint(18, Compound.Soft), Stint(12, Compound.Medium), Stint(10, Compound.Hard))),
    )

    fun compoundColor(c: Compound): Color = when (c) {
        Compound.Soft -> Color(0xFFFF5252)
        Compound.Medium -> Color(0xFFFFC107)
        Compound.Hard -> Color(0xFFE0E0E0)
    }
}
