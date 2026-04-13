package com.pitwall.app.hotpath

import android.content.Context
import android.util.Log
import com.pitwall.app.data.TelemetryFrame
import com.pitwall.app.data.CoachingMessage
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "GemmaEngine"
private const val MODEL_FILENAME = "gemma-3-1b-it-int4.bin"

/**
 * Gemma 3 1B on-device inference engine via MediaPipe LLM Inference Task.
 * On Pixel 10: GPU/TPU delegate → <50ms.
 * On other devices: CPU fallback (slower but functional).
 *
 * Falls back to the rule-based SonicModel if the model file is missing
 * or inference fails.
 */
class GemmaEngine(private val context: Context) {

    private var llm: LlmInference? = null
    private var isReady = false
    val available get() = isReady

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        val modelFile = File(context.filesDir, MODEL_FILENAME)
        if (!modelFile.exists()) {
            Log.w(TAG, "Gemma model not found at ${modelFile.absolutePath}. " +
                    "Run: adb push $MODEL_FILENAME /data/data/com.pitwall.app/files/")
            return@withContext false
        }
        try {
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(64)           // coaching cues are short (<25 words)
                // setTemperature(0.1f)       // deterministic — important for safety-critical coaching
                // .setRandomSeed(42)
                // GPU delegate → automatically uses TPU on Pixel 10 via the ML accelerator
                /*
                .apply {
                    try {
                        // Attempt hardware acceleration
                        val backendClass = Class.forName(
                            "com.google.mediapipe.tasks.genai.llminference.LlmInference\$Backend"
                        )
                        val gpuBackend = backendClass.getField("GPU").get(null)
                        javaClass.getMethod("setPreferredBackend", backendClass)
                            .invoke(this, gpuBackend)
                    } catch (e: Exception) {
                        Log.d(TAG, "Hardware acceleration not available, using CPU")
                    }
                }
                */
                .build()
            llm = LlmInference.createFromOptions(context, options)
            isReady = true
            Log.i(TAG, "Gemma 3 1B loaded from ${modelFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Gemma: ${e.message}")
            false
        }
    }

    /**
     * Evaluate a TelemetryFrame and return a coaching message, or null if no cue is warranted.
     * Runs on IO dispatcher — caller collects result on main/hotpath thread.
     */
    suspend fun evaluate(
        frame: TelemetryFrame,
        vectors: List<PedagogicalVector>,
        driverLevel: DriverLevel,
        recentMessages: List<String>,
    ): CoachingMessage? = withContext(Dispatchers.Default) {
        val engine = llm ?: return@withContext null

        val matchedVectors = vectors.filter { it.matches(frame) }.take(3)
        if (matchedVectors.isEmpty()) return@withContext null

        val prompt = buildPrompt(frame, matchedVectors, driverLevel, recentMessages)

        try {
            val response = engine.generateResponse(prompt).trim()
            if (response.isBlank() || response.equals("none", ignoreCase = true)) return@withContext null

            val priority = when {
                frame.comboG.value > TelemetryFrame.MAX_COMBO_G * 1.05f -> 3  // over limit = safety
                matchedVectors.any { it.priority == 3 } -> 3
                matchedVectors.any { it.priority == 2 } -> 2
                else -> 1
            }

            CoachingMessage(
                text = response,
                priority = priority,
                source = CoachingMessage.Source.HOT_PATH,
                targetCorner = frame.currentCorner,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gemma inference failed: ${e.message}")
            null
        }
    }

    private fun buildPrompt(
        frame: TelemetryFrame,
        vectors: List<PedagogicalVector>,
        level: DriverLevel,
        recentMessages: List<String>,
    ): String = buildString {
        appendLine("You are a racing coach riding shotgun. The driver is ${level.display}.")
        appendLine("Respond ONLY when you detect a coaching moment. Keep responses under 5 words " +
                "for reflexive cues, under 15 for technique. Safety alerts are immediate.")
        appendLine("NEVER speak during heavy cornering (gLat > 0.8G) unless safety-critical.")
        appendLine()
        appendLine("MATCHED COACHING VECTORS:")
        vectors.forEach { v -> appendLine("  - ${v.concept}: ${v.instruction}") }
        appendLine()
        appendLine("CURRENT FRAME:")
        appendLine("  Speed: ${frame.speedKmh.toInt()} km/h | Brake: ${frame.brake.value.toInt()} bar | " +
                "Throttle: ${frame.throttle.value.toInt()}%")
        appendLine("  gLat: ${frame.gLat.value} G | gLong: ${frame.gLong.value} G | " +
                "Combo: ${frame.comboG.value} G")
        appendLine("  Corner: ${frame.currentCorner ?: "none"} | Past apex: ${frame.pastApex}")
        appendLine("  Brake conf: ${frame.brake.confidence} | Speed conf: ${frame.speed.confidence}")
        appendLine()
        if (recentMessages.isNotEmpty()) {
            appendLine("RECENT (avoid repeating):")
            recentMessages.takeLast(3).forEach { appendLine("  - $it") }
        }
        appendLine()
        appendLine("Respond with the coaching cue only, or 'none' if no cue is needed.")
    }

    fun close() {
        llm?.close()
        llm = null
        isReady = false
    }
}

enum class DriverLevel(val display: String) {
    BEGINNER("beginner (rental car)"),
    INTERMEDIATE("intermediate (BMW M3)"),
    PRO("pro (race car)"),
}
