package com.pitwall.paddock.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SessionSummary(
    @SerialName("session_id") val sessionId: String,
    val driver: String,
    @SerialName("driver_level") val driverLevel: String,
    val track: String,
    val car: String,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    val note: String = "",
    @SerialName("lap_count") val lapCount: Int = 0,
    @SerialName("best_lap_s") val bestLapS: Float? = null
)

@Serializable
data class SessionListResponse(
    val sessions: List<SessionSummary>,
    val count: Int
)

@Serializable
data class SessionDetailResponse(
    val session: SessionSummary,
    val laps: List<LapDetail>,
    val notes: List<CoachingNote>,
    @SerialName("lap_count") val lapCount: Int,
    @SerialName("best_lap_s") val bestLapS: Float? = null
)

@Serializable
data class LapDetail(
    @SerialName("lap_number") val lapNumber: Int,
    @SerialName("lap_time_s") val lapTimeS: Float? = null,
    @SerialName("best_sector") val bestSector: Float? = null,
    @SerialName("avg_speed_kmh") val avgSpeedKmh: Float? = null,
    @SerialName("max_combo_g") val maxComboG: Float? = null,
    @SerialName("coast_pct") val coastPct: Float? = null,
    @SerialName("recorded_at") val recordedAt: String? = null
)

@Serializable
data class CoachingNote(
    val id: Int? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("burst_id") val burstId: Int,
    @SerialName("distance_m") val distanceM: Float,
    val text: String,
    val source: String,
    @SerialName("recorded_at") val recordedAt: String? = null
)

@Serializable
data class BriefResponse(
    @SerialName("driver_id") val driverId: String,
    val date: String,
    val emotion: String, // "neutral", "focused", "tense"
    @SerialName("narrative_md") val narrativeMd: String,
    val focus: List<String> = emptyList(), // corner IDs
    @SerialName("danger_zones_today") val dangerZonesToday: List<String> = emptyList(),
    @SerialName("surface_state") val surfaceState: String = "",
    @SerialName("weather_note") val weatherNote: String = "",
    @SerialName("weakest_recent_corner") val weakestRecentCorner: String? = null,
    @SerialName("biggest_recent_improvement") val biggestRecentImprovement: String? = null
)

@Serializable
data class LapTimeTableResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("lap_count") val lapCount: Int,
    @SerialName("best_lap_s") val bestLapS: Float = 0f,
    @SerialName("best_lap_number") val bestLapNumber: Int = 0,
    val laps: List<LapTimeTableRow>
)

@Serializable
data class LapTimeTableRow(
    @SerialName("lap_number") val lapNumber: Int,
    @SerialName("lap_time_s") val lapTimeS: Float,
    @SerialName("delta_to_best_s") val deltaToBestS: Float,
    @SerialName("is_best") val isBest: Boolean,
    val sectors: List<SectorDetail> = emptyList()
)

@Serializable
data class SectorDetail(
    val name: String,
    @SerialName("time_s") val timeS: Float,
    @SerialName("is_best") val isBest: Boolean
)

@Serializable
data class ScorecardResponse(
    @SerialName("session_id") val sessionId: String,
    val scorecard: Map<String, CornerGrade>
)

@Serializable
data class CornerGrade(
    @SerialName("entry_grade") val entryGrade: String,
    @SerialName("apex_grade") val apexGrade: String,
    @SerialName("exit_grade") val exitGrade: String,
    @SerialName("avg_speed_delta_kmh") val avgSpeedDeltaKmh: Float,
    @SerialName("max_g") val maxG: Float,
    @SerialName("improvement_pct") val improvementPct: Float = 0f
)

@Serializable
data class PedalBehaviorResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("frame_count") val frameCount: Int,
    @SerialName("frame_dt_s") val frameDtS: Float,
    val states: Map<String, PedalStateDetail>
)

@Serializable
data class PedalStateDetail(
    val frames: Int,
    val pct: Float,
    @SerialName("time_s") val timeS: Float
)

@Serializable
data class StartSessionRequest(
    val driver: String? = null,
    @SerialName("driver_level") val driverLevel: String? = null,
    val track: String? = null,
    val car: String? = null,
    val note: String? = null
)

@Serializable
data class StartSessionResponse(
    val started: Boolean,
    @SerialName("session_id") val sessionId: String
)

@Serializable
data class EndSessionResponse(
    val ended: Boolean,
    @SerialName("session_id") val sessionId: String
)

@Serializable
data class ConceptsResponse(
    val concepts: List<CoachingConcept>
)

@Serializable
data class CoachingConcept(
    val id: String,
    val description: String,
    @SerialName("fires_when") val firesWhen: String
)

@Serializable
data class SignalRegistryResponse(
    val signals: List<HardwareSignal>
)

@Serializable
data class HardwareSignal(
    @SerialName("signal_id") val signalId: Int? = null,
    val name: String,
    val units: String? = null,
    @SerialName("expected_hz") val expectedHz: Int? = null,
    val group: String? = null,
    val discovery: String? = null
)

@Serializable
data class DebriefRequest(
    @SerialName("session_id") val sessionId: String
)

@Serializable
data class DebriefResponse(
    val debrief: String
)

@Serializable
data class AskRequest(
    val query: String,
    val context: JsonObject? = null
)

@Serializable
data class AskResponse(
    val text: String,
    val error: String? = null
)

@Serializable
data class InsightsResponse(
    val insights: List<DriverInsightRaw>
)

@Serializable
data class DriverInsightRaw(
    val id: String,
    val rank: Int? = null,
    val title: String,
    val detail: String,
    val corners: List<String>? = null,
    @SerialName("metric_label") val metricLabel: String? = null,
    @SerialName("metric_value") val metricValue: String? = null,
    val effort: Int? = null,
    @SerialName("est_gain_s") val estGainS: Float? = null,
    @SerialName("evidence_bursts") val evidenceBursts: Int? = null
)

@Serializable
data class TelemetryFrame(
    val speed: Float,
    @SerialName("combo_g") val comboG: Float,
    @SerialName("lateral_g") val lateralG: Float,
    @SerialName("long_g") val longG: Float,
    @SerialName("throttle_pct") val throttlePct: Float,
    @SerialName("brake_bar") val brakeBar: Float,
    @SerialName("steering_deg") val steeringDeg: Float,
    val distance: Float,
    @SerialName("lap_number") val lapNumber: Int,
    @SerialName("in_corner") val inCorner: Boolean,
    @SerialName("past_apex") val pastApex: Boolean,
    @SerialName("frame_type") val frameType: String
)

@Serializable
data class CueEvent(
    val text: String,
    val priority: Int,
    @SerialName("corner_id") val cornerId: String? = null,
    @SerialName("cue_type") val cueType: String
)
