package com.pitwall.bridge.ktor

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

@Serializable
data class HealthPayload(
    val status: String = "ok",
    val version: String = "2.0-embedded",
    val engine: String = "embedded_ktor",
    val coach: String? = null,
    @SerialName("driver_level") val driverLevel: String = "intermediate",
    val track: String? = null,
    val duckdb: Boolean = true,
    @SerialName("active_session_id") val activeSessionId: String? = null,
    @SerialName("can_bridge") val canBridge: Boolean = true,
    val timestamp: String,
)

@Serializable
data class AnalyzeResponsePayload(
    val coaching: String,
    @SerialName("pace_note") val paceNote: String? = null,
    @SerialName("coach_source") val coachSource: String? = null,
    val cues: JsonArray = JsonArray(emptyList()),
    @SerialName("burst_id") val burstId: Int = 0,
    /** Matches Vue `AnalyzeResponse.source`: `sonic_model` | `bridge_rules` */
    val source: String = "bridge_rules",
)
