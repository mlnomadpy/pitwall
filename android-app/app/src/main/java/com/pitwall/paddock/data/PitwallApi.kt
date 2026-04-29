package com.pitwall.paddock.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET

interface PitwallApi {
    @GET("health")
    suspend fun health(): HealthResponse
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
