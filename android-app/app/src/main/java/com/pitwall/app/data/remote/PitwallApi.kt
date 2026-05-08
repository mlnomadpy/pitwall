package com.pitwall.app.data.remote

import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * HTTP surface for the Pitwall Flask bridge ([docs/api.md]).
 * Bundle-style paths return [JsonObject] for flexible analysis payloads; stable shapes are typed.
 */
interface PitwallApi {
    @GET("health")
    suspend fun health(): HealthResponse

    @GET("sessions")
    suspend fun sessions(
        @Query("limit") limit: Int = 50,
        @Query("active_only") activeOnly: Boolean = false,
    ): SessionsEnvelope

    @GET("session/{id}")
    suspend fun getSession(@Path("id") sessionId: String): SessionDetailDto

    @POST("session/start")
    suspend fun startSession(@Body body: StartSessionRequest): StartSessionResponse

    @POST("session/{id}/end")
    suspend fun endSession(@Path("id") sessionId: String): EndSessionResponse

    @POST("analyze")
    suspend fun analyze(@Body body: AnalyzeRequestDto): AnalyzeResponseDto

    // Lap / ideal / distribution
    @GET("session/{id}/lap_time_table")
    suspend fun lapTimeTable(@Path("id") sessionId: String): LapTimeTableDto

    @GET("session/{id}/lap_time_distribution")
    suspend fun lapTimeDistribution(@Path("id") sessionId: String): LapTimeDistributionDto

    @GET("session/{id}/ideal_lap")
    suspend fun idealLap(@Path("id") sessionId: String): IdealLapDto

    @GET("session/{id}/sector_times")
    suspend fun sectorTimes(@Path("id") sessionId: String): SectorTimesResponseDto

    // Session analysis bundle (after POST /coach/debrief)
    @GET("session/{id}/scorecard")
    suspend fun scorecard(@Path("id") sessionId: String): ScorecardEnvelopeDto

    @GET("session/{id}/highlights")
    suspend fun sessionHighlights(@Path("id") sessionId: String): JsonObject

    @GET("session/{id}/stats")
    suspend fun sessionStats(@Path("id") sessionId: String): JsonObject

    @GET("session/{id}/friction_circle")
    suspend fun sessionFrictionCircle(@Path("id") sessionId: String): JsonObject

    @GET("session/{id}/hustle_map")
    suspend fun sessionHustleMap(@Path("id") sessionId: String): JsonObject

    @GET("session/{id}/eob")
    suspend fun sessionEob(@Path("id") sessionId: String): JsonObject

    @GET("session/{id}/incidents")
    suspend fun sessionIncidents(@Path("id") sessionId: String): JsonObject

    @GET("session/{id}/map")
    suspend fun sessionMap(@Path("id") sessionId: String): JsonObject

    @GET("session/{id}/clips")
    suspend fun sessionClips(@Path("id") sessionId: String): SessionClipsEnvelopeDto

    // Telemetry-derived aggregates
    @GET("session/{id}/pedal_behavior")
    suspend fun pedalBehavior(
        @Path("id") sessionId: String,
        @Query("throttle_th") throttleTh: Double? = null,
        @Query("brake_th") brakeTh: Double? = null,
    ): PedalBehaviorDto

    @GET("session/{id}/straight_line_speed")
    suspend fun straightLineSpeed(@Path("id") sessionId: String): StraightLineSpeedDto

    @GET("session/{id}/throttle_corner_box")
    suspend fun throttleCornerBox(@Path("id") sessionId: String): JsonObject

    @GET("session/{id}/corner_classification")
    suspend fun cornerClassification(
        @Path("id") sessionId: String,
        @Query("low_max") lowMax: Double? = null,
        @Query("med_max") medMax: Double? = null,
    ): JsonObject

    @GET("session/{id}/brake_acceleration")
    suspend fun brakeAcceleration(@Path("id") sessionId: String): BrakeAccelerationDto

    @GET("signals/registry")
    suspend fun signalsRegistry(): JsonObject

    @GET("session/{id}/capabilities")
    suspend fun sessionCapabilities(@Path("id") sessionId: String): JsonObject

    @GET("session/{id}/signals")
    suspend fun sessionSignalsGet(
        @Path("id") sessionId: String,
        @Query("names") names: String,
        @Query("axis") axis: String? = null,
        @Query("interp") interp: String? = null,
        @Query("rate_hz") rateHz: Double? = null,
        @Query("t_from") tFrom: Double? = null,
        @Query("t_to") tTo: Double? = null,
    ): JsonObject

    @POST("session/{id}/signals")
    suspend fun sessionSignalsPost(
        @Path("id") sessionId: String,
        @Body body: JsonObject,
    ): JsonObject

    @POST("session/{id}/capabilities/recompute")
    suspend fun sessionCapabilitiesRecompute(@Path("id") sessionId: String): JsonObject

    @GET("coach/brief")
    suspend fun coachBrief(
        @Query("driver") driver: String? = null,
        @Query("focus") focus: String? = null,
        @Query("goal") goal: String? = null,
        @Query("session_id") sessionId: String? = null,
    ): BriefResponseDto

    @POST("coach/debrief")
    suspend fun coachDebrief(@Body body: DebriefRequestDto): JsonObject

    @POST("coach/ask")
    suspend fun coachAsk(@Body body: CoachAskRequestDto): CoachAskResponseDto

    @POST("coach/ask/end")
    suspend fun coachAskEnd(@Body body: CoachAskEndRequestDto): CoachAskEndResponseDto

    @GET("coach/concepts")
    suspend fun coachConcepts(): CoachConceptsResponse

    @GET("coach/agents")
    suspend fun coachAgents(): JsonObject

    @POST("score")
    suspend fun scoreSession(@Body body: LlmScoreRequestDto): LlmScoreResponseDto

    @GET("insights")
    suspend fun insights(@Query("lap") lap: Int? = null): InsightsResponseDto

    @GET("conversations/{sid}")
    suspend fun conversationsForSession(@Path("sid") sessionId: String): JsonObject

    @GET("conversations/driver/{driverId}")
    suspend fun conversationsForDriver(
        @Path("driverId") driverId: String,
        @Query("limit") limit: Int? = null,
    ): JsonObject

    @GET("driver/{driverId}/evolution")
    suspend fun driverEvolution(
        @Path("driverId") driverId: String,
        @Query("track") track: String? = null,
    ): JsonObject

    @GET("session/{id}/corners")
    suspend fun sessionCornersAggregate(@Path("id") sessionId: String): JsonObject

    @GET("diagnostics/llm_friction")
    suspend fun diagnosticsLlmFriction(
        @Query("session_id") sessionId: String? = null,
        @Query("role") role: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("since_minutes") sinceMinutes: Double? = null,
    ): JsonObject

    // ── Track reference (bp_track) ──────────────────────────────────────────
    @GET("track/markers")
    suspend fun trackMarkers(): JsonObject

    @GET("track/danger_zones")
    suspend fun trackDangerZones(): JsonObject

    @GET("track/weather")
    suspend fun trackWeather(@Query("hour_local") hourLocal: Int? = null): JsonObject

    @GET("track/{id}/elevation")
    suspend fun trackElevation(
        @Path("id") trackId: String,
        @Query("step_m") stepM: Double? = null,
    ): JsonObject

    @GET("markers")
    suspend fun markersFiltered(
        @Query("corner") corner: String? = null,
        @Query("kind") kind: String? = null,
    ): JsonObject

    @GET("laps")
    suspend fun laps(
        @Query("session_id") sessionId: String? = null,
        @Query("limit") limit: Int? = null,
    ): JsonObject

    @POST("lap")
    suspend fun postLap(@Body body: JsonObject): JsonObject

    @GET("driver/{driverId}/profile")
    suspend fun driverProfile(@Path("driverId") driverId: String): JsonObject

    // ── Session ingest / export / sync (bp_session) ─────────────────────────
    @POST("session/{id}/frames")
    suspend fun postSessionFrames(
        @Path("id") sessionId: String,
        @Body body: JsonObject,
    ): JsonObject

    @POST("session/{id}/video_frames")
    suspend fun postSessionVideoFrames(
        @Path("id") sessionId: String,
        @Body body: JsonObject,
    ): JsonObject

    @POST("session/{id}/frame")
    suspend fun postSessionFrame(
        @Path("id") sessionId: String,
        @Body body: JsonObject,
    ): JsonObject

    @GET("session/{id}/sync")
    suspend fun sessionSync(
        @Path("id") sessionId: String,
        @Query("from") from: Double? = null,
        @Query("to") to: Double? = null,
        @Query("window_s") windowS: Double? = null,
    ): JsonObject

    @Streaming
    @GET("session/{id}/export.parquet")
    suspend fun sessionExportParquet(
        @Path("id") sessionId: String,
        @Query("table") table: String? = null,
    ): ResponseBody

    @POST("session/import")
    suspend fun sessionImport(@Body body: JsonObject): JsonObject

    @POST("session/reset")
    suspend fun sessionReset(): JsonObject
}
