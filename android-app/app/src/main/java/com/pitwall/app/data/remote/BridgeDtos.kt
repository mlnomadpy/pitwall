package com.pitwall.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

/** Mirrors [src/pwa/src/shared/types/bridge.ts] — keep in sync. */
@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val engine: String,
    val coach: String? = null,
    @SerialName("driver_level") val driverLevel: String,
    val track: String? = null,
    val duckdb: Boolean,
    val timestamp: String,
)

@Serializable
data class SessionsEnvelope(
    val sessions: List<SessionSummaryDto>,
    val count: Int,
)

@Serializable
data class SessionSummaryDto(
    @SerialName("session_id") val sessionId: String,
    val driver: String,
    @SerialName("driver_level") val driverLevel: String,
    val track: String,
    val car: String,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    val note: String,
    @SerialName("lap_count") val lapCount: Int,
    @SerialName("best_lap_s") val bestLapS: Double? = null,
)

@Serializable
data class StartSessionRequest(
    val driver: String? = null,
    @SerialName("driver_level") val driverLevel: String? = null,
    val track: String? = null,
    val car: String? = null,
    val note: String? = null,
)

@Serializable
data class StartSessionResponse(
    val started: Boolean,
    @SerialName("session_id") val sessionId: String,
)

@Serializable
data class EndSessionResponse(
    val ended: Boolean,
    @SerialName("session_id") val sessionId: String,
)

@Serializable
data class SessionDetailDto(
    val session: SessionSummaryDto,
    val laps: List<LapDetailDto>,
    val notes: List<CoachingNoteDto>,
    @SerialName("lap_count") val lapCount: Int,
    @SerialName("best_lap_s") val bestLapS: Double? = null,
)

@Serializable
data class LapDetailDto(
    @SerialName("lap_number") val lapNumber: Int,
    @SerialName("lap_time_s") val lapTimeS: Double? = null,
    @SerialName("best_sector") val bestSector: Double? = null,
    @SerialName("avg_speed_kmh") val avgSpeedKmh: Double? = null,
    @SerialName("max_combo_g") val maxComboG: Double? = null,
    @SerialName("coast_pct") val coastPct: Double? = null,
    @SerialName("recorded_at") val recordedAt: String? = null,
)

@Serializable
data class CoachingNoteDto(
    val id: Int? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("burst_id") val burstId: Int,
    @SerialName("distance_m") val distanceM: Double,
    val text: String,
    val source: String,
    @SerialName("recorded_at") val recordedAt: String? = null,
)

@Serializable
data class AnalyzeRequestDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("burst_id") val burstId: Int,
    @SerialName("avg_speed_kmh") val avgSpeedKmh: Double,
    @SerialName("max_combo_g") val maxComboG: Double,
    @SerialName("max_lateral_g") val maxLateralG: Double? = null,
    @SerialName("max_long_g") val maxLongG: Double,
    @SerialName("max_brake_bar") val maxBrakeBar: Double,
    @SerialName("avg_throttle_pct") val avgThrottlePct: Double,
    @SerialName("avg_steering_deg") val avgSteeringDeg: Double,
    @SerialName("coast_frames") val coastFrames: Int,
    @SerialName("trail_brake_frames") val trailBrakeFrames: Int,
    @SerialName("frame_count") val frameCount: Int,
    @SerialName("corners_visited") val cornersVisited: List<String>,
    @SerialName("distance_m") val distanceM: Double,
    @SerialName("in_corner") val inCorner: Boolean,
    @SerialName("past_apex") val pastApex: Boolean,
)

@Serializable
data class AnalyzeResponseDto(
    val coaching: String,
    @SerialName("pace_note") val paceNote: String,
    @SerialName("coach_source") val coachSource: String,
    val cues: JsonArray? = null,
    @SerialName("burst_id") val burstId: Int,
    val source: String,
)

@Serializable
data class LapSectorDto(
    val name: String,
    @SerialName("time_s") val timeS: Double,
    @SerialName("is_best") val isBest: Boolean,
)

@Serializable
data class LapTimeRowDto(
    @SerialName("lap_number") val lapNumber: Int,
    @SerialName("lap_time_s") val lapTimeS: Double,
    @SerialName("delta_to_best_s") val deltaToBestS: Double,
    @SerialName("is_best") val isBest: Boolean,
    val sectors: List<LapSectorDto>,
)

@Serializable
data class LapTimeTableDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("lap_count") val lapCount: Int,
    @SerialName("best_lap_s") val bestLapS: Double,
    @SerialName("best_lap_number") val bestLapNumber: Int,
    val laps: List<LapTimeRowDto>,
)

@Serializable
data class BriefResponseDto(
    @SerialName("driver_id") val driverId: String,
    val date: String,
    @SerialName("weather_phase") val weatherPhase: String,
    @SerialName("surface_state") val surfaceState: String,
    @SerialName("weather_note") val weatherNote: String,
    @SerialName("weakest_recent_corner") val weakestRecentCorner: String? = null,
    @SerialName("biggest_recent_improvement") val biggestRecentImprovement: String? = null,
    @SerialName("danger_zones_today") val dangerZonesToday: List<String> = emptyList(),
    @SerialName("narrative_md") val narrativeMd: String,
    val focus: List<String> = emptyList(),
    val emotion: String,
)

@Serializable
data class DebriefRequestDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("driver_id") val driverId: String = "",
    @SerialName("vbo_path") val vboPath: String? = null,
)

@Serializable
data class CoachAskRequestDto(
    val question: String,
    @SerialName("driver_id") val driverId: String = "",
    @SerialName("session_id") val sessionId: String = "",
    val intent: String? = null,
)

@Serializable
data class CoachAskResponseDto(
    val answer: String? = null,
    val emotion: String? = null,
    @SerialName("qa_key") val qaKey: String? = null,
    val turn: Int? = null,
    val error: String? = null,
)

@Serializable
data class CoachConceptsResponse(
    val source: String,
    val concepts: List<CoachConceptDto>,
    val count: Int,
)

@Serializable
data class CoachConceptDto(
    val id: String,
    val description: String,
    @SerialName("fires_when") val firesWhen: String,
)

@Serializable
data class InsightsResponseDto(
    val insights: List<InsightItemDto>,
    @SerialName("session_bursts") val sessionBursts: Int,
    @SerialName("generated_at") val generatedAt: String,
)

@Serializable
data class InsightItemDto(
    val id: String,
    val title: String,
    val detail: String,
    val corners: List<String> = emptyList(),
    @SerialName("metric_label") val metricLabel: String? = null,
    @SerialName("metric_value") val metricValue: String? = null,
    val effort: Int? = null,
    @SerialName("est_gain_s") val estGainS: Double? = null,
    @SerialName("evidence_bursts") val evidenceBursts: Int? = null,
    val rank: Int? = null,
)

@Serializable
data class LapTimeDistributionDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("lap_count") val lapCount: Int,
    @SerialName("min_s") val minS: Double,
    @SerialName("max_s") val maxS: Double,
    @SerialName("q1_s") val q1S: Double,
    @SerialName("median_s") val medianS: Double,
    @SerialName("q3_s") val q3S: Double,
    @SerialName("iqr_s") val iqrS: Double,
    @SerialName("whisker_low_s") val whiskerLowS: Double,
    @SerialName("whisker_high_s") val whiskerHighS: Double,
    val outliers: List<OutlierLapDto> = emptyList(),
    @SerialName("mean_s") val meanS: Double,
    @SerialName("stddev_s") val stddevS: Double,
)

@Serializable
data class OutlierLapDto(
    @SerialName("lap_number") val lapNumber: Int,
    @SerialName("lap_time_s") val lapTimeS: Double,
)

@Serializable
data class IdealLapDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("ideal_lap_s") val idealLapS: Double,
    @SerialName("best_actual_lap_s") val bestActualLapS: Double,
    @SerialName("gain_potential_s") val gainPotentialS: Double,
    @SerialName("best_sectors") val bestSectors: List<BestSectorDto>,
)

@Serializable
data class BestSectorDto(
    val name: String,
    @SerialName("time_s") val timeS: Double,
    @SerialName("from_lap") val fromLap: Int,
)

/** Wrapper from `GET /session/{id}/scorecard` (`bp_analysis._section`). */
@Serializable
data class ScorecardEnvelopeDto(
    @SerialName("session_id") val sessionId: String? = null,
    val scorecard: SessionScorecardDto? = null,
    val error: String? = null,
)

@Serializable
data class SessionScorecardDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("n_laps") val nLaps: Int,
    @SerialName("best_lap_s") val bestLapS: Double,
    @SerialName("gold_lap_s") val goldLapS: Double,
    @SerialName("session_grade") val sessionGrade: String,
    @SerialName("weighted_total_pct") val weightedTotalPct: Double,
    val summary: String,
    val corners: List<CornerGradeDto> = emptyList(),
)

@Serializable
data class TimeLossAttributionDto(
    val cause: String,
    @SerialName("seconds_lost") val secondsLost: Double,
    val detail: String,
)

@Serializable
data class CornerGradeDto(
    val corner: String,
    val lap: Int,
    val grade: String,
    @SerialName("score_pct") val scorePct: Double,
    val weight: Double,
    @SerialName("delta_time_s") val deltaTimeS: Double,
    @SerialName("entry_delta_kmh") val entryDeltaKmh: Double,
    @SerialName("apex_delta_kmh") val apexDeltaKmh: Double,
    @SerialName("exit_delta_kmh") val exitDeltaKmh: Double,
    @SerialName("brake_point_delta_m") val brakePointDeltaM: Double? = null,
    @SerialName("trail_brake_quality") val trailBrakeQuality: Double,
    @SerialName("time_loss_attribution") val timeLossAttribution: List<TimeLossAttributionDto> = emptyList(),
    @SerialName("trod_voice") val trodVoice: String,
)

// ── GET /session/{id}/pedal_behavior ───────────────────────────────────────

@Serializable
data class PedalBehaviorDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("frame_count") val frameCount: Int,
    val thresholds: PedalThresholdsDto,
    @SerialName("frame_dt_s") val frameDtS: Double,
    val states: Map<String, PedalStateBucketDto>,
)

@Serializable
data class PedalThresholdsDto(
    @SerialName("throttle_pct") val throttlePct: Double,
    @SerialName("brake_bar") val brakeBar: Double,
)

@Serializable
data class PedalStateBucketDto(
    val frames: Int,
    val pct: Double,
    @SerialName("time_s") val timeS: Double,
)

// ── GET /session/{id}/straight_line_speed ──────────────────────────────────

@Serializable
data class StraightLineSpeedDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("track_length_m") val trackLengthM: Double,
    val straights: List<StraightSpeedRowDto>,
)

@Serializable
data class StraightSpeedRowDto(
    val name: String,
    @SerialName("start_m") val startM: Double,
    @SerialName("end_m") val endM: Double,
    @SerialName("top_speed_kmh") val topSpeedKmh: Double? = null,
    @SerialName("from_lap") val fromLap: Int? = null,
)

// ── GET /session/{id}/sector_times ─────────────────────────────────────────

@Serializable
data class SectorTimesResponseDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("sector_definitions") val sectorDefinitions: List<SectorDefinitionDto>,
    val laps: List<SectorTimesLapDto>,
)

@Serializable
data class SectorDefinitionDto(
    val name: String,
    @SerialName("start_m") val startM: Double,
    @SerialName("end_m") val endM: Double,
)

@Serializable
data class SectorTimesLapDto(
    @SerialName("lap_number") val lapNumber: Int,
    val s1: Double,
    val s2: Double,
    val s3: Double,
)

// ── GET /session/{id}/clips ────────────────────────────────────────────────

@Serializable
data class SessionClipsEnvelopeDto(
    @SerialName("session_id") val sessionId: String,
    val clips: List<SessionClipDto> = emptyList(),
    val count: Int,
)

@Serializable
data class SessionClipDto(
    val id: String,
    val title: String,
    @SerialName("in_s") val inS: Double,
    @SerialName("out_s") val outS: Double,
    val category: String,
    val severity: String,
    val lap: Int,
)

// ── GET /session/{id}/brake_acceleration ───────────────────────────────────

@Serializable
data class BrakeAccelerationDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("brake_zones") val brakeZones: List<BrakeZoneAggDto>,
    @SerialName("corner_exits") val cornerExits: List<CornerExitAccelDto>,
)

@Serializable
data class BrakeZoneAggDto(
    val corner: String,
    @SerialName("max_decel_g") val maxDecelG: Double,
    @SerialName("duration_s") val durationS: Double,
    @SerialName("n_passes") val nPasses: Int,
)

@Serializable
data class CornerExitAccelDto(
    val corner: String,
    @SerialName("max_long_accel_g") val maxLongAccelG: Double,
    @SerialName("exit_speed_kmh") val exitSpeedKmh: Double,
    @SerialName("n_passes") val nPasses: Int,
)

// ── POST /coach/ask/end ────────────────────────────────────────────────────

@Serializable
data class CoachAskEndRequestDto(
    @SerialName("driver_id") val driverId: String = "",
    @SerialName("session_id") val sessionId: String = "",
)

@Serializable
data class CoachAskEndResponseDto(
    val flushed: Int,
    @SerialName("qa_key") val qaKey: String,
)

// ── POST /score ─────────────────────────────────────────────────────────────

@Serializable
data class LlmScoreRequestDto(
    @SerialName("session_id") val sessionId: String,
    val focus: String = "",
    @SerialName("driver_level") val driverLevel: String = "intermediate",
)

@Serializable
data class LlmScoreResponseDto(
    @SerialName("session_id") val sessionId: String,
    val score: Int,
    val why: String,
    val model: String? = null,
    val focus: String? = null,
)
