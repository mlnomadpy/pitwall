package com.pitwall.paddock.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface PitwallApi {
    @GET("health")
    suspend fun health(): HealthResponse

    @POST("analyze")
    suspend fun analyze(@Body body: AnalyzeBurstRequest): AnalyzeBurstResponse
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
