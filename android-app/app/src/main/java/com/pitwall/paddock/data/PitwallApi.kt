package com.pitwall.paddock.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET

interface PitwallApi {
    @GET("health")
    suspend fun health(): HealthResponse

    @GET("sessions")
    suspend fun sessions(@retrofit2.http.Query("limit") limit: Int = 50): SessionListResponse

    @GET("session/{sid}")
    suspend fun session(@retrofit2.http.Path("sid") sid: String): SessionDetailResponse

    @retrofit2.http.POST("session/start")
    suspend fun startSession(@retrofit2.http.Body req: StartSessionRequest): StartSessionResponse

    @retrofit2.http.POST("session/{sid}/end")
    suspend fun endSession(@retrofit2.http.Path("sid") sid: String): EndSessionResponse

    @GET("session/{sid}/lap_time_table")
    suspend fun lapTimeTable(@retrofit2.http.Path("sid") sid: String): LapTimeTableResponse

    @GET("session/{sid}/scorecard")
    suspend fun scorecard(@retrofit2.http.Path("sid") sid: String): ScorecardResponse

    @GET("session/{sid}/pedal_behavior")
    suspend fun pedalBehavior(@retrofit2.http.Path("sid") sid: String): PedalBehaviorResponse

    @GET("coach/brief")
    suspend fun brief(
        @retrofit2.http.Query("driver") driver: String,
        @retrofit2.http.Query("track") track: String? = null,
        @retrofit2.http.Query("session_id") sessionId: String? = null
    ): BriefResponse

    @retrofit2.http.POST("coach/debrief")
    suspend fun debrief(@retrofit2.http.Body req: DebriefRequest): DebriefResponse

    @retrofit2.http.POST("coach/ask")
    suspend fun ask(@retrofit2.http.Body req: AskRequest): AskResponse

    @GET("insights")
    suspend fun insights(): InsightsResponse

    @GET("signals/registry")
    suspend fun signalRegistry(): SignalRegistryResponse

    @GET("coach/concepts")
    suspend fun concepts(): ConceptsResponse
}

@Serializable
data class HealthResponse(
    val status: String = "",
    val version: String = "",
    val engine: String? = null,
    val coach: String? = null,
    @SerialName("driver_level")
    val driverLevel: String? = null,
    val track: String? = null,
    val duckdb: Boolean? = null,
    val timestamp: String? = null,
)
