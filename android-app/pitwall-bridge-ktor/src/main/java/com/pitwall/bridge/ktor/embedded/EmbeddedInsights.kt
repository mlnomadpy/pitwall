package com.pitwall.bridge.ktor.embedded

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/**
 * Lightweight port of Flask `_score_insights` for `/insights`.
 */
internal object EmbeddedInsights {

    fun scoreInsights(bursts: List<JsonObject>, lapFilter: Int?): JsonObject {
        var snapshot = bursts
        if (lapFilter != null) {
            snapshot = snapshot.filter { it["lap"]?.jsonPrimitive?.intOrNull == lapFilter }
        }
        if (snapshot.isEmpty()) {
            return insightsEnvelope(JsonArray(emptyList()), snapshot.size)
        }

        val totalFrames = snapshot.sumOf { it["frame_count"]?.jsonPrimitive?.intOrNull ?: 1 }.coerceAtLeast(1)
        val coastFrames = snapshot.sumOf { it["coast_frames"]?.jsonPrimitive?.intOrNull ?: 0 }
        val trailFrames = snapshot.sumOf { it["trail_brake_frames"]?.jsonPrimitive?.intOrNull ?: 0 }
        val cornerBursts = snapshot.filter { it["corners_visited"]?.jsonArray?.isNotEmpty() == true }
        val allG = snapshot.mapNotNull { it["max_combo_g"]?.jsonPrimitive?.doubleOrNull }
        val avgG = if (allG.isNotEmpty()) allG.sum() / allG.size else 0.0
        val avgSpeed = snapshot.mapNotNull { it["avg_speed_kmh"]?.jsonPrimitive?.doubleOrNull }
            .average().takeIf { !it.isNaN() } ?: 0.0
        val coastPct = (coastFrames.toDouble() / totalFrames) * 100.0
        val gripHeadroom = 2.29 - avgG

        val insights = mutableListOf<JsonObject>()
        val coastCorners = mutableListOf<String>()
        val gripCorners = mutableListOf<String>()
        for (b in cornerBursts) {
            val fc = b["frame_count"]?.jsonPrimitive?.intOrNull?.coerceAtLeast(1) ?: 1
            val cf = b["coast_frames"]?.jsonPrimitive?.intOrNull ?: 0
            val cornerNames = b["corners_visited"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            if (cf.toDouble() / fc > 0.20) coastCorners.addAll(cornerNames)
            val mg = b["max_combo_g"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            if (mg < 1.5) gripCorners.addAll(cornerNames)
        }

        if (coastPct > 15) {
            insights.add(
                insight(
                    id = "coast_excess",
                    title = "Early Throttle Pickup",
                    detail = "You're coasting ${coastPct.toInt()}% of sampled bursts. Aim for earlier throttle at apex.",
                    corners = coastCorners.distinct().take(4),
                    metricLabel = "Coast",
                    metricValue = "${coastPct.toInt()}%",
                    effort = 1,
                    estGain = minOf(coastPct * 0.03, 1.5),
                    evidence = snapshot.count { (it["coast_frames"]?.jsonPrimitive?.intOrNull ?: 0) > 0 },
                ),
            )
        }
        if (avgG < 1.6 && gripCorners.distinct().size >= 2) {
            insights.add(
                insight(
                    id = "grip_headroom",
                    title = "Unused Grip Budget",
                    detail = "Peak G averaging ${"%.2f".format(avgG)}G — tyres support ~2.29G.",
                    corners = gripCorners.distinct().take(4),
                    metricLabel = "Peak G",
                    metricValue = "${"%.2f".format(avgG)}G",
                    effort = 1,
                    estGain = minOf(gripHeadroom * 0.4, 1.0),
                    evidence = gripCorners.distinct().size,
                ),
            )
        }
        val inCornerBursts = cornerBursts.filter {
            it["in_corner"]?.jsonPrimitive?.booleanOrNull == true
        }
        if (inCornerBursts.isNotEmpty() && trailFrames == 0) {
            val trailCorners = inCornerBursts.flatMap { b ->
                b["corners_visited"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            }.distinct().take(4)
            insights.add(
                insight(
                    id = "trail_absent",
                    title = "Add Trail Braking",
                    detail = "No trail braking detected in sampled bursts.",
                    corners = trailCorners,
                    metricLabel = "Trail frames",
                    metricValue = "0",
                    effort = 2,
                    estGain = 0.4,
                    evidence = inCornerBursts.size,
                ),
            )
        }

        val slowEntry = mutableListOf<String>()
        for (b in cornerBursts) {
            val sp = b["avg_speed_kmh"]?.jsonPrimitive?.doubleOrNull ?: 999.0
            val inc = b["in_corner"]?.jsonPrimitive?.booleanOrNull == true
            if (sp < 70 && inc) {
                slowEntry.addAll(b["corners_visited"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList())
            }
        }
        if (slowEntry.isNotEmpty()) {
            insights.add(
                insight(
                    id = "braking_late",
                    title = "Brake Point Optimisation",
                    detail = "Corner entry averaging ${avgSpeed.toInt()} km/h — consider a slightly later brake point.",
                    corners = slowEntry.distinct().take(4),
                    metricLabel = "Avg entry",
                    metricValue = "${avgSpeed.toInt()} km/h",
                    effort = 2,
                    estGain = 0.5,
                    evidence = slowEntry.distinct().size,
                ),
            )
        }

        val sorted = insights.sortedWith(
            compareBy<JsonObject>(
                { it["effort"]?.jsonPrimitive?.intOrNull ?: 99 },
                { -(it["est_gain_s"]?.jsonPrimitive?.doubleOrNull ?: 0.0) },
            ),
        ).take(3).mapIndexed { i, o ->
            JsonObject(o.toMutableMap().apply { put("rank", JsonPrimitive(i + 1)) })
        }

        return insightsEnvelope(JsonArray(sorted), snapshot.size)
    }

    private fun insightsEnvelope(insights: JsonArray, burstCount: Int) = kotlinx.serialization.json.buildJsonObject {
        put("insights", insights)
        put("session_bursts", JsonPrimitive(burstCount))
        put("generated_at", JsonPrimitive(Instant.now().toString()))
    }

    private fun insight(
        id: String,
        title: String,
        detail: String,
        corners: List<String>,
        metricLabel: String,
        metricValue: String,
        effort: Int,
        estGain: Double,
        evidence: Int,
    ): JsonObject = kotlinx.serialization.json.buildJsonObject {
        put("id", JsonPrimitive(id))
        put("title", JsonPrimitive(title))
        put("detail", JsonPrimitive(detail))
        put("corners", JsonArray(corners.map { JsonPrimitive(it) }))
        put("metric_label", JsonPrimitive(metricLabel))
        put("metric_value", JsonPrimitive(metricValue))
        put("effort", JsonPrimitive(effort))
        put("est_gain_s", JsonPrimitive(estGain))
        put("evidence_bursts", JsonPrimitive(evidence))
        put("rank", JsonPrimitive(0))
    }

    private fun JsonObject.toMutableMap(): MutableMap<String, kotlinx.serialization.json.JsonElement> {
        val m = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        for (entry in entries) {
            m[entry.key] = entry.value
        }
        return m
    }
}
