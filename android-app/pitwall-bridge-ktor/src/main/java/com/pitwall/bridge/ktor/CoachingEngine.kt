package com.pitwall.bridge.ktor

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Instant

/**
 * Bridge-domain coaching: health payload + /analyze.
 *
 * Resolution order for inference:
 * 1. Optional HTTP backend ([PitwallLlmHttpClient]) — same contract as `android-llm-service` on port 8080.
 * 2. In-process MediaPipe when a `.task` file exists at [llmModelAbsolutePath].
 * 3. Stub text when neither is available.
 */
interface CoachingEngine {
    fun healthPayload(): HealthPayload

    suspend fun analyze(burstJson: String): AnalyzeResponsePayload
}

class DefaultCoachingEngine(
    private val context: Context,
    private val llmModelAbsolutePath: String?,
    /** e.g. `http://127.0.0.1:8080` — empty/null disables HTTP and uses local `.task` only. */
    private val llmHttpBaseUrl: String? = null,
    /** Model id forwarded to `/v1/chat/completions` (must match what `android-llm-service` expects). */
    private val llmHttpModel: String = "gemma-4-E2B-it",
) : CoachingEngine {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val inferenceMutex = Mutex()
    private var llmInference: LlmInference? = null
    private val httpBase: String? =
        llmHttpBaseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
    private val sessionOptions by lazy {
        LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(40)
            .setTopP(0.95f)
            .setTemperature(0.7f)
            .build()
    }

    init {
        val path = llmModelAbsolutePath?.takeIf { it.isNotBlank() }
        val file = path?.let { File(it) }
        if (file != null && file.isFile) {
            try {
                val opts = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(file.absolutePath)
                    .setMaxTokens(1024)
                    .build()
                llmInference = LlmInference.createFromOptions(context, opts)
            } catch (_: Throwable) {
                llmInference = null
            }
        }
    }

    override fun healthPayload(): HealthPayload {
        val ts = Instant.now().toString()
        val localLoaded = llmInference != null
        val httpConfigured = httpBase != null
        val engine = when {
            localLoaded && httpConfigured -> "sonic_model+llm_http"
            localLoaded -> "sonic_model"
            httpConfigured -> "llm_http"
            else -> "rules"
        }
        val coach = when {
            localLoaded -> "litert"
            httpConfigured -> "litert"
            else -> null
        }
        return HealthPayload(
            status = "ok",
            version = "2.0-embedded",
            engine = engine,
            coach = coach,
            driverLevel = "intermediate",
            track = null,
            duckdb = true,
            activeSessionId = null,
            canBridge = true,
            timestamp = ts,
        )
    }

    override suspend fun analyze(burstJson: String): AnalyzeResponsePayload = inferenceMutex.withLock {
        val burstId = parseBurstId(burstJson)
        val prompt =
            "You are a racing engineer. Given this telemetry burst JSON, reply with one short coaching sentence only.\n$burstJson"

        val httpUrl = httpBase
        if (httpUrl != null) {
            val model = llmHttpModel.trim().ifEmpty { "gemma-4-E2B-it" }
            val viaHttp = PitwallLlmHttpClient.chatCompletion(
                baseUrl = httpUrl,
                model = model,
                sessionId = "pitwall-analyze-$burstId",
                userPrompt = prompt,
            )
            if (viaHttp != null) {
                return AnalyzeResponsePayload(
                    coaching = viaHttp,
                    paceNote = null,
                    coachSource = "litert",
                    burstId = burstId,
                    source = "sonic_model",
                )
            }
        }

        val inference = llmInference
        val coaching = if (inference != null) {
            runMediaPipe(inference, prompt)
        } else {
            buildString {
                append(
                    "Embedded Ktor stub — start android-llm-service on 8080, set PITWALL_LLM_HTTP_BASE_URL, ",
                )
                append("or add a .task at PITWALL_LLM_MODEL_PATH (see README).")
            }
        }
        AnalyzeResponsePayload(
            coaching = coaching,
            paceNote = null,
            coachSource = if (inference != null) "litert" else null,
            burstId = burstId,
            source = if (inference != null) "sonic_model" else "bridge_rules",
        )
    }

    private fun parseBurstId(raw: String): Int =
        try {
            json.parseToJsonElement(raw).jsonObject["burst_id"]?.jsonPrimitive?.intOrNull ?: 0
        } catch (_: Throwable) {
            0
        }

    private suspend fun runMediaPipe(inference: LlmInference, fullPrompt: String): String =
        withContext(Dispatchers.IO) {
            val session = LlmInferenceSession.createFromOptions(inference, sessionOptions)
            try {
                session.addQueryChunk(fullPrompt)
                session.generateResponse()
            } finally {
                session.close()
            }
        }
}
