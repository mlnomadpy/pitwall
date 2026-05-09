package com.pitwall.bridge.ktor.embedded

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.math.sqrt

/**
 * Lap time distribution / quantiles aligned with Flask `session_lap_time_distribution`.
 */
internal object EmbeddedLapStats {

    fun lapTimeDistribution(sessionId: String, laps: List<EmbeddedDuckDb.LapRow>): JsonObject {
        val withTime = laps.mapNotNull { row ->
            val t = row.lapTimeS ?: return@mapNotNull null
            row.lapNumber to t
        }
        if (withTime.isEmpty()) {
            return buildJsonObject {
                put("error", JsonPrimitive("no complete laps detected"))
                put("session_id", JsonPrimitive(sessionId))
            }
        }
        val times = withTime.map { it.second }.sorted()
        val n = times.size
        val q1 = quantile(times, 0.25)
        val q2 = quantile(times, 0.50)
        val q3 = quantile(times, 0.75)
        val iqr = q3 - q1
        val loFence = q1 - 1.5 * iqr
        val hiFence = q3 + 1.5 * iqr
        val inRange = times.filter { it >= loFence && it <= hiFence }
        val whiskerLow = inRange.minOrNull() ?: times.first()
        val whiskerHigh = inRange.maxOrNull() ?: times.last()
        val outliers = buildJsonArray {
            for ((lapNo, t) in withTime) {
                if (t < loFence || t > hiFence) {
                    add(
                        buildJsonObject {
                            put("lap_number", JsonPrimitive(lapNo))
                            put("lap_time_s", JsonPrimitive(round3(t)))
                        },
                    )
                }
            }
        }
        val mu = times.sum() / n
        val variance = times.sumOf { (it - mu) * (it - mu) } / n
        val sigma = sqrt(variance)
        return buildJsonObject {
            put("session_id", JsonPrimitive(sessionId))
            put("lap_count", JsonPrimitive(n))
            put("min_s", JsonPrimitive(round3(times.first())))
            put("max_s", JsonPrimitive(round3(times.last())))
            put("q1_s", JsonPrimitive(round3(q1)))
            put("median_s", JsonPrimitive(round3(q2)))
            put("q3_s", JsonPrimitive(round3(q3)))
            put("iqr_s", JsonPrimitive(round3(iqr)))
            put("whisker_low_s", JsonPrimitive(round3(whiskerLow)))
            put("whisker_high_s", JsonPrimitive(round3(whiskerHigh)))
            put("outliers", outliers)
            put("mean_s", JsonPrimitive(round3(mu)))
            put("stddev_s", JsonPrimitive(round3(sigma)))
        }
    }

    fun quantile(sortedAsc: List<Double>, q: Double): Double {
        if (sortedAsc.isEmpty()) return 0.0
        val idx = (sortedAsc.size - 1) * q
        val lo = idx.toInt().coerceIn(0, sortedAsc.lastIndex)
        val hi = (lo + 1).coerceIn(0, sortedAsc.lastIndex)
        val frac = idx - lo
        return sortedAsc[lo] * (1 - frac) + sortedAsc[hi] * frac
    }

    private fun round3(x: Double): Double = kotlin.math.round(x * 1000.0) / 1000.0
}
