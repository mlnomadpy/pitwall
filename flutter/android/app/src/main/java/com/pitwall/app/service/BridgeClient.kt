package com.pitwall.app.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "BridgeClient"

/**
 * Thin HTTP client for the local Python pitwall_bridge.py server.
 * Reachable via `adb reverse tcp:8765 tcp:8765`.
 */
class BridgeClient(private val baseUrl: String = "http://127.0.0.1:8765") {

    private val http = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /** POST telemetry burst JSON to /analyze. Returns coaching text or null. */
    suspend fun analyze(burstJson: String): String? = withContext(Dispatchers.IO) {
        try {
            val body = burstJson.toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url("$baseUrl/analyze").post(body).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val payload = resp.body?.string() ?: return@withContext null
                payload.substringAfter("\"coaching\":\"", "")
                    .substringBefore("\"").trim().ifBlank { null }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Bridge unavailable: ${e.message}")
            null
        }
    }

    /** GET /insights — returns raw JSON string or null. */
    suspend fun getInsightsJson(): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$baseUrl/insights").get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) { null }
    }

    /** GET /health — returns true if bridge is reachable. */
    suspend fun isReachable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$baseUrl/health").get().build()
            http.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }
}
