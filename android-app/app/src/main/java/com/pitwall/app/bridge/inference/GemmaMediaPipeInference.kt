package com.pitwall.app.bridge.inference

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import com.pitwall.app.data.remote.BriefResponseDto
import com.pitwall.app.data.remote.CoachAskResponseDto
import com.pitwall.app.data.remote.LlmScoreResponseDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device Gemma via **MediaPipe LLM Inference** (`com.google.mediapipe:tasks-genai`) —
 * aligned with https://ai.google.dev/gemma/docs/integrations/mobile
 *
 * Drop **`Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task`** into either:
 * - app internal storage (`filesDir`), or
 * - `assets/gemma/` (copied on first run).
 *
 * If the model is missing or inference fails, falls back to [StubNativeCoachInference].
 */
class GemmaMediaPipeInference(
    private val context: Context,
    private val fallback: StubNativeCoachInference = StubNativeCoachInference(),
) : NativeCoachInference {

    private val tag = "GemmaMediaPipeInference"

    private val initMutex = Mutex()
    private val generateMutex = Mutex()

    @Volatile
    private var llm: LlmInference? = null

    private companion object {
        const val GEMMA_TASK_FILENAME = "Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task"
        private const val ASSET_SUBDIR = "gemma"
        private const val MAX_MODEL_TOKENS = 1024
        private const val GENERATE_TIMEOUT_MS = 120_000L
    }

    override suspend fun scoreSession(
        sessionId: String,
        focus: String,
        driverLevel: String,
    ): LlmScoreResponseDto {
        val prompt =
            buildString {
                appendLine("You are an AI racing coach for Pitwall.")
                appendLine("Session: $sessionId. Driver level: $driverLevel. Focus: ${focus.ifBlank { "overall" }}.")
                appendLine(
                    "Reply with exactly two lines: line 1 = integer session score from 0 to 100 only; line 2 = one short sentence why.",
                )
            }
        val raw =
            try {
                generateWithModel(prompt)
            } catch (_: Exception) {
                return fallback.scoreSession(sessionId, focus, driverLevel)
            }
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val score =
            lines.firstOrNull()?.toIntOrNull()?.coerceIn(0, 100)
                ?: Regex("(\\d{1,3})").find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 100)
                ?: 70
        val why = lines.getOrNull(1) ?: raw.trim().lines().drop(1).joinToString(" ").ifBlank { raw.take(400) }
        return LlmScoreResponseDto(
            sessionId = sessionId,
            score = score,
            why = why,
            model = "gemma3-1b-it-mediapipe",
            focus = focus.ifBlank { null },
        )
    }

    override suspend fun coachAsk(
        question: String,
        driverId: String,
        sessionId: String,
    ): CoachAskResponseDto =
        try {
            val prompt =
                buildString {
                    appendLine("You are Pitwall, an AI racing coach. Answer clearly and briefly.")
                    appendLine("Driver: ${driverId.ifBlank { "unknown" }}. Session: ${sessionId.ifBlank { "n/a" }}.")
                    appendLine("Question: $question")
                }
            val answer = generateWithModel(prompt).trim()
            CoachAskResponseDto(
                answer = answer,
                emotion = "neutral",
                qaKey = "gemma",
                turn = 1,
                error = null,
            )
        } catch (_: Exception) {
            fallback.coachAsk(question, driverId, sessionId)
        }

    override suspend fun coachBrief(
        driver: String?,
        focus: String?,
        sessionId: String?,
    ): BriefResponseDto =
        try {
            val prompt =
                buildString {
                    appendLine("You are Pitwall AI racing coach. Write a short pre-session markdown brief (under 150 words).")
                    appendLine("Driver: ${driver ?: "unknown"}. Focus: ${focus ?: "race prep"}. Session: ${sessionId ?: "n/a"}.")
                    appendLine("End with a single word mood on the last line: focused, calm, or aggressive.")
                }
            val raw = generateWithModel(prompt)
            val lines = raw.trimEnd().lines()
            val mood =
                lines.lastOrNull()?.trim()?.lowercase()?.takeIf {
                    it in setOf("focused", "calm", "aggressive")
                } ?: "focused"
            BriefResponseDto(
                driverId = driver.orEmpty().ifBlank { "driver" },
                date = Instant.now().toString(),
                weatherPhase = "clear",
                surfaceState = "dry",
                weatherNote = "On-device Gemma brief.",
                weakestRecentCorner = null,
                biggestRecentImprovement = null,
                dangerZonesToday = emptyList(),
                narrativeMd = raw.trim(),
                focus = listOfNotNull(focus?.takeIf { it.isNotBlank() }),
                emotion = mood,
            )
        } catch (_: Exception) {
            fallback.coachBrief(driver, focus, sessionId)
        }

    private suspend fun generateWithModel(prompt: String): String {
        if (!ensureEngineReady()) error("model_unavailable")
        val engine = llm ?: error("engine_unavailable")
        return generateMutex.withLock {
            withTimeout(GENERATE_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    val sessionOptions =
                        LlmInferenceSessionOptions.builder()
                            .setTemperature(0.8f)
                            .setTopK(64)
                            .setTopP(0.95f)
                            .build()
                    val session = LlmInferenceSession.createFromOptions(engine, sessionOptions)
                    try {
                        session.addQueryChunk(prompt)
                        val out = StringBuilder()
                        val finished = CompletableDeferred<Unit>()
                        val sawDone = AtomicBoolean(false)
                        session.generateResponseAsync { partial: String, done: Boolean ->
                            out.append(partial)
                            if (done && sawDone.compareAndSet(false, true)) {
                                finished.complete(Unit)
                            }
                        }
                        finished.await()
                        var text = out.toString()
                        if (text.isBlank()) {
                            delay(50)
                            text = out.toString()
                        }
                        text.ifBlank { "(empty model output)" }
                    } finally {
                        session.close()
                    }
                }
            }
        }
    }

    private suspend fun ensureEngineReady(): Boolean {
        if (llm != null) return true
        val path = resolveModelPath() ?: return false
        initMutex.withLock {
            if (llm != null) return@withLock
            llm =
                try {
                    createEngine(path, LlmInference.Backend.GPU)
                } catch (e: Exception) {
                    Log.w(tag, "GPU backend failed, trying CPU (${e.message})")
                    try {
                        createEngine(path, LlmInference.Backend.CPU)
                    } catch (e2: Exception) {
                        Log.e(tag, "Failed to load Gemma MediaPipe engine", e2)
                        null
                    }
                }
        }
        return llm != null
    }

    private fun createEngine(
        modelPath: String,
        backend: LlmInference.Backend,
    ): LlmInference {
        val options =
            LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(MAX_MODEL_TOKENS)
                .setPreferredBackend(backend)
                .build()
        return LlmInference.createFromOptions(context, options)
    }

    /** Prefers existing file in [Context.getFilesDir]; otherwise copies from `assets/gemma/`. */
    private suspend fun resolveModelPath(): String? =
        withContext(Dispatchers.IO) {
            val target = File(context.filesDir, GEMMA_TASK_FILENAME)
            if (target.exists() && target.length() > 0L) {
                return@withContext target.absolutePath
            }
            try {
                context.assets.open("$ASSET_SUBDIR/$GEMMA_TASK_FILENAME").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                target.absolutePath
            } catch (e: Exception) {
                Log.i(
                    tag,
                    "No Gemma task bundle — place $GEMMA_TASK_FILENAME in filesDir or assets/gemma/ (${e.message})",
                )
                null
            }
        }
}
