package com.pitwall.app.bridge.inference

import com.pitwall.app.data.remote.BriefResponseDto
import com.pitwall.app.data.remote.CoachAskResponseDto
import com.pitwall.app.data.remote.LlmScoreResponseDto

/** Deterministic stand-in until a `.tflite` / LiteRT engine is wired in assets. */
class StubNativeCoachInference : NativeCoachInference {
    override suspend fun scoreSession(
        sessionId: String,
        focus: String,
        driverLevel: String,
    ): LlmScoreResponseDto =
        LlmScoreResponseDto(
            sessionId = sessionId,
            score = 72,
            why =
                "Kotlin native bridge (stub inference). Hook Gemma/LiteRT in NativeCoachInference.",
            model = "stub-kotlin",
            focus = focus.ifBlank { null },
        )

    override suspend fun coachAsk(
        question: String,
        driverId: String,
        sessionId: String,
    ): CoachAskResponseDto =
        CoachAskResponseDto(
            answer =
                "[stub] On-device LLM not loaded yet. Question was: ${question.take(120)}",
            emotion = "neutral",
            qaKey = "stub",
            turn = 1,
            error = null,
        )

    override suspend fun coachBrief(
        driver: String?,
        focus: String?,
        sessionId: String?,
    ): BriefResponseDto =
        BriefResponseDto(
            driverId = driver.orEmpty().ifBlank { "driver" },
            date = java.time.Instant.now().toString(),
            weatherPhase = "clear",
            surfaceState = "dry",
            weatherNote = "Native bridge brief (stub).",
            weakestRecentCorner = null,
            biggestRecentImprovement = null,
            dangerZonesToday = emptyList(),
            narrativeMd = "## Brief\n\nConnect a LiteRT model asset to replace this stub.",
            focus = listOfNotNull(focus?.takeIf { it.isNotBlank() }),
            emotion = "focused",
        )
}
