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
 * Bridge-domain coaching: health payload + /analyze. Stub when no model; MediaPipe when [.task] exists.
 */
interface CoachingEngine {
    fun healthPayload(): HealthPayload

    suspend fun analyze(burstJson: String): AnalyzeResponsePayload
}

class DefaultCoachingEngine(
    private val context: Context,
    private val llmModelAbsolutePath: String?,
) : CoachingEngine {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val inferenceMutex = Mutex()
    private var llmInference: LlmInference? = null
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
        val modelLoaded = llmInference != null
        return HealthPayload(
            status = "ok",
            version = "2.0-embedded",
            engine = if (modelLoaded) "sonic_model" else "rules",
            coach = if (modelLoaded) "litert" else null,
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
        val inference = llmInference
        val coaching = if (inference != null) {
            runMediaPipe(inference, burstJson)
        } else {
            "Embedded Ktor stub — add .task at PITWALL_LLM_MODEL_PATH (see README)."
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

    private suspend fun runMediaPipe(inference: LlmInference, burstJson: String): String =
        withContext(Dispatchers.IO) {
            val prompt =
                "You are a racing engineer. Given this telemetry burst JSON, reply with one short coaching sentence only.\n$burstJson"
            val session = LlmInferenceSession.createFromOptions(inference, sessionOptions)
            try {
                session.addQueryChunk(prompt)
                session.generateResponse()
            } finally {
                session.close()
            }
        }
}
