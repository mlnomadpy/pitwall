package com.pitwall.paddock.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

@Serializable
data class AnalyzeBurstRequest(
    @SerialName("session_id") val sessionId: String? = "native-parallel-demo",
    @SerialName("burst_id") val burstId: Int = 1,
    @SerialName("avg_speed_kmh") val avgSpeedKmh: Double = 104.0,
    @SerialName("max_combo_g") val maxComboG: Double = 1.72,
)

@Serializable
data class AnalyzeBurstResponse(
    val coaching: String,
    @SerialName("pace_note") val paceNote: String? = null,
    @SerialName("coach_source") val coachSource: String? = null,
    val cues: JsonArray = JsonArray(emptyList()),
    @SerialName("burst_id") val burstId: Int = 0,
    val source: String = "",
)
