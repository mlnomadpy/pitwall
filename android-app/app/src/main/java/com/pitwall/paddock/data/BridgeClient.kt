package com.pitwall.paddock.data

import android.util.Log
import com.pitwall.paddock.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "BridgeClient"

/**
 * Thin HTTP client for the local Python pitwall_bridge.py server.
 * Reachable via `adb reverse tcp:8765 tcp:8765` or set PITWALL_API_BASE_URL in local.properties.
 *
 * Ported from android/service/BridgeClient.kt — uses BuildConfig.PITWALL_API_BASE_URL
 * instead of hardcoded 127.0.0.1.
 */
class BridgeClient(
    private val baseUrl: String = BuildConfig.PITWALL_API_BASE_URL,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** POST telemetry burst JSON to /analyze. Returns coaching text or null. */
    suspend fun analyze(burstJson: String): String? = withContext(Dispatchers.IO) {
        try {
            val body = burstJson.toRequestBody("application/json".toMediaType())
            val req  = Request.Builder()
                .url("$baseUrl/analyze")
                .header("Connection", "close")
                .post(body)
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val payload = resp.body?.string() ?: return@withContext null
                payload.substringAfter("\"coaching\":\"", "")
                    .substringBefore("\"").trim().ifBlank { null }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Bridge analyze unavailable: ${e.message}")
            null
        }
    }

    /** GET /insights?lap=N — returns raw JSON string or null. */
    suspend fun getInsightsJson(lap: Int? = null): String? = withContext(Dispatchers.IO) {
        try {
            val url = if (lap != null) "$baseUrl/insights?lap=$lap" else "$baseUrl/insights"
            val req = Request.Builder()
                .url(url)
                .header("Connection", "close")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Bridge insights unavailable: ${e.message}")
            null
        }
    }

    /** GET /health — returns true if bridge is reachable. */
    suspend fun isReachable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$baseUrl/health")
                .header("Connection", "close")
                .get()
                .build()
            http.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    /** SSE: /telemetry/stream */
    fun telemetryStream(sid: String): kotlinx.coroutines.flow.Flow<TelemetryFrame> = kotlinx.coroutines.flow.callbackFlow {
        val req = Request.Builder().url("$baseUrl/telemetry/stream?session_id=$sid").build()
        val factory = okhttp3.sse.EventSources.createFactory(http)
        val listener = object : okhttp3.sse.EventSourceListener() {
            override fun onEvent(eventSource: okhttp3.sse.EventSource, id: String?, type: String?, data: String) {
                try {
                    val frame = Json { ignoreUnknownKeys = true }.decodeFromString<TelemetryFrame>(data)
                    trySend(frame)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse telemetry: ${e.message}")
                }
            }
            override fun onFailure(eventSource: okhttp3.sse.EventSource, t: Throwable?, response: okhttp3.Response?) {
                Log.e(TAG, "Telemetry stream failed: ${t?.message}")
                // In production, might want to implement reconnect logic here if okhttp-sse doesn't auto-reconnect
            }
            override fun onClosed(eventSource: okhttp3.sse.EventSource) {
                close()
            }
        }
        val es = factory.newEventSource(req, listener)
        awaitClose { es.cancel() }
    }

    /** SSE: /cues/stream */
    fun cueStream(sid: String): kotlinx.coroutines.flow.Flow<CueEvent> = kotlinx.coroutines.flow.callbackFlow {
        val req = Request.Builder().url("$baseUrl/cues/stream?session_id=$sid").build()
        val factory = okhttp3.sse.EventSources.createFactory(http)
        val listener = object : okhttp3.sse.EventSourceListener() {
            override fun onEvent(eventSource: okhttp3.sse.EventSource, id: String?, type: String?, data: String) {
                try {
                    val cue = Json { ignoreUnknownKeys = true }.decodeFromString<CueEvent>(data)
                    trySend(cue)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse cue: ${e.message}")
                }
            }
            override fun onFailure(eventSource: okhttp3.sse.EventSource, t: Throwable?, response: okhttp3.Response?) {
                Log.e(TAG, "Cue stream failed: ${t?.message}")
            }
            override fun onClosed(eventSource: okhttp3.sse.EventSource) {
                close()
            }
        }
        val es = factory.newEventSource(req, listener)
        awaitClose { es.cancel() }
    }
}
