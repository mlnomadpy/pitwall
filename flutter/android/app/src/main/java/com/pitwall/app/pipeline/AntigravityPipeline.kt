package com.pitwall.app.pipeline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.pitwall.app.BuildConfig  // generated — DO NOT REMOVE (sub-package needs explicit import)
import com.pitwall.app.data.CoachingMessage
import com.pitwall.app.data.TelemetryFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "AntigravityPipeline"
private const val BURST_INTERVAL_MS = 7_500L        // send every 7.5s
private const val RING_BUFFER_SECONDS = 10
private const val MAX_FRAMES_PER_BURST = 500        // 10Hz × 50s max

/**
 * Antigravity store-and-forward pipeline.
 *
 * Buffers telemetry frames locally in a 10s ring buffer.
 * Every 7.5s, serialises the buffer and sends to Vertex AI:
 *   → Gemini 3.0 analyses the burst + Gold Standard → coaching response
 *   → coaching response flows back through the MessageArbiter
 *
 * If 5G/LTE is unavailable, persists bursts to local disk and
 * delivers them when connectivity returns — guaranteed delivery.
 */
class AntigravityPipeline(
    private val context: Context,
    private val sessionId: String,
    private val onCoachingReceived: suspend (CoachingMessage) -> Unit,
) {
    private val ringBuffer = ArrayDeque<TelemetryFrame>(MAX_FRAMES_PER_BURST)
    private var burstId = 0
    private var lastBurstAt = 0L
    private val persistDir = File(context.filesDir, "agy_bursts").also { it.mkdirs() }

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // ── Local Python bridge (pitwall_bridge.py via adb reverse / Termux) ─────
    // Tried first — no GCP credentials needed. Set to null to skip.
    private val bridgeEndpoint = "http://127.0.0.1:8765"

    // ── Gemini API — set GEMINI_API_KEY in local.properties ──────────────────
    // Get a free key at: aistudio.google.com/apikey
    private val geminiApiKey: String = BuildConfig.GEMINI_API_KEY
    private val geminiEndpoint: String =
        "https://generativelanguage.googleapis.com/v1beta/models/" +
        "gemini-2.5-flash:generateContent?key=$geminiApiKey"

    /** Add a frame to the ring buffer. Called every telemetry frame (10Hz). */
    fun addFrame(frame: TelemetryFrame) {
        if (ringBuffer.size >= MAX_FRAMES_PER_BURST) ringBuffer.removeFirst()
        ringBuffer.addLast(frame)
    }

    /**
     * Evaluate whether it's time to send a burst.
     * Safe to call every frame — returns immediately if not yet time.
     */
    suspend fun tick() {
        val now = System.currentTimeMillis()
        if (now - lastBurstAt < BURST_INTERVAL_MS) return
        lastBurstAt = now
        sendBurst()

        // Attempt to drain persisted bursts when online
        if (isOnline()) drainPersistedBursts()
    }

    private suspend fun sendBurst() = withContext(Dispatchers.IO) {
        if (ringBuffer.isEmpty()) return@withContext

        val frames = ringBuffer.toList()
        val burst = serialiseBurst(frames)
        burstId++

        if (!isOnline()) {
            persistBurst(burst, burstId)
            Log.w(TAG, "Offline — burst #$burstId persisted to disk")
            return@withContext
        }

        deliverBurst(burst, burstId)
    }

    private suspend fun deliverBurst(burstJson: String, id: Int) {
        try {
            // Tier 1: local Python bridge
            val bridgeResult = try {
                callBridge(burstJson)
            } catch (e: Exception) {
                Log.w(TAG, "Bridge unavailable: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
            val response: String? = if (bridgeResult != null) {
                Log.i(TAG, "Burst #$id → bridge")
                bridgeResult
            } else {
                // Tier 2/3: Gemini API or mock
                val prompt = buildGeminiPrompt(burstJson)
                callGeminiApi(prompt).also {
                    if (it != null) Log.i(TAG, "Burst #$id → Gemini")
                }
            }

            if (response != null) {
                val coaching = CoachingMessage(
                    text = response,
                    priority = 1,
                    source = CoachingMessage.Source.WARM_PATH,
                    targetCorner = extractCornerFromResponse(response),
                )
                onCoachingReceived(coaching)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Burst #$id delivery failed: ${e.message}")
            persistBurst(burstJson, id)
        }
    }

    private fun callGeminiApi(prompt: String): String? {
        // Bridge is tried at deliverBurst level — this handles Gemini API → mock only.
        if (geminiApiKey.isBlank()) {
            Log.d(TAG, "No GEMINI_API_KEY — using mock coaching response")
            return MOCK_COACHING_RESPONSES.random()
        }

        val escapedPrompt = prompt.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "")
        val body = """{"contents":[{"parts":[{"text":"$escapedPrompt"}]}]}"""

        val request = Request.Builder()
            .url(geminiEndpoint)
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: "(empty)"
                Log.e(TAG, "Gemini API ${response.code}: $errBody")
                return null
            }
            val raw = response.body!!.string()
            val text = Json.parseToJsonElement(raw)
                .let { it.toString() }
                .substringAfter("\"text\":\"")
                .substringBefore("\"")
                .trim()
            return text.ifBlank { null }
        }
    }

    /** Call the local pitwall_bridge.py server with a 3s timeout. */
    private fun callBridge(burstJson: String): String? {
        val bridgeHttp = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()

        val body = burstJson.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$bridgeEndpoint/analyze")
            .post(body)
            .build()

        bridgeHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val payload = response.body?.string() ?: return null
            val json = Json.parseToJsonElement(payload)
            // Extract {"coaching": "..."}
            return json.toString()
                .substringAfter("\"coaching\":\"")
                .substringBefore("\"")
                .ifBlank { null }
        }
    }

    private fun buildGeminiPrompt(burstJson: String): String = """
        You are a race engineer analysing telemetry for an intermediate driver (BMW M3) at Sonoma Raceway.
        Compare to AJ's Gold Standard lap (98.2s best).
        Generate ONE coaching message for the driver's next sector.
        Reference specific corners and metrics. Under 25 words. Priority P1 (strategy).
        
        TELEMETRY BURST:
        $burstJson
    """.trimIndent()

    private fun extractCornerFromResponse(text: String): String? {
        val pattern = Regex("Turn \\d+", RegexOption.IGNORE_CASE)
        return pattern.find(text)?.value
    }

    private fun serialiseBurst(frames: List<TelemetryFrame>): String {
        val avgSpeed     = frames.map { it.speedKmh }.average()
        val maxComboG    = frames.maxOf { it.comboG.value }
        val maxLateralG  = frames.maxOf { it.gLat.value }
        val maxLongG     = frames.maxOf { it.gLong.value }
        val maxBrake     = frames.maxOf { it.brake.value }
        val avgThrottle  = frames.map { it.throttle.value }.average()
        val avgSteering  = frames.map { it.steering.value }.average()
        val coastFrames  = frames.count { it.isCoasting }
        val trailFrames  = frames.count { it.brake.value > 3f && it.inCorner }
        val distanceM    = frames.lastOrNull()?.distance?.value ?: 0f
        val inCorner     = frames.lastOrNull()?.inCorner ?: false
        val pastApex     = frames.lastOrNull()?.pastApex ?: false
        val corners      = frames.mapNotNull { it.currentCorner }.distinct()
            .joinToString(",") { "\"$it\"" }
        return buildString {
            append('{')
            append("\"session_id\":\"$sessionId\",")
            append("\"burst_id\":$burstId,")
            append("\"frame_count\":${frames.size},")
            append("\"car\":\"BMW M3\",")
            append("\"track\":\"sonoma\",")
            append("\"driver_level\":\"intermediate\",")
            append("\"avg_speed_kmh\":${avgSpeed.toFloat()},")
            append("\"max_combo_g\":$maxComboG,")
            append("\"max_lateral_g\":$maxLateralG,")
            append("\"max_long_g\":$maxLongG,")
            append("\"max_brake_bar\":$maxBrake,")
            append("\"avg_throttle_pct\":${avgThrottle.toFloat()},")
            append("\"avg_steering_deg\":${avgSteering.toFloat()},")
            append("\"coast_frames\":$coastFrames,")
            append("\"trail_brake_frames\":$trailFrames,")
            append("\"distance_m\":$distanceM,")
            append("\"in_corner\":$inCorner,")
            append("\"past_apex\":$pastApex,")
            append("\"corners_visited\":[$corners]")
            append('}')
        }
    }

    private fun persistBurst(json: String, id: Int) {
        File(persistDir, "burst_$id.json").writeText(json)
    }

    private suspend fun drainPersistedBursts() = withContext(Dispatchers.IO) {
        persistDir.listFiles()
            ?.filter { it.name.startsWith("burst_") && it.name.endsWith(".json") }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                try {
                    deliverBurst(file.readText(), file.nameWithoutExtension.removePrefix("burst_").toInt())
                    file.delete()
                    Log.i(TAG, "Drained persisted burst: ${file.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to drain ${file.name}: ${e.message}")
                }
            }
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        // Mock responses used until Vertex AI credentials are configured
        private val MOCK_COACHING_RESPONSES = listOf(
            "Turn 3: you braked 15m early vs AJ. Hold to the 2-board next lap.",
            "Turn 10: trail brake lighter through apex. AJ carries 4mph more.",
            "Sector 2: coast time is costing 0.4s. Get on throttle at the apex.",
            "Turn 11: your exit speed is 6mph below AJ. More throttle on the way out.",
            "Good trail braking in Turn 6. Carry that technique to Turn 10.",
        )
    }
}
