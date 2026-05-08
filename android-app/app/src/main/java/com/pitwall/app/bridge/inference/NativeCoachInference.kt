package com.pitwall.app.bridge.inference

import com.pitwall.app.data.remote.BriefResponseDto
import com.pitwall.app.data.remote.CoachAskResponseDto
import com.pitwall.app.data.remote.LlmScoreResponseDto

/**
 * On-device coaching / scoring. Production builds use [GemmaMediaPipeInference] (MediaPipe LLM
 * Inference + Gemma `.task`, see https://ai.google.dev/gemma/docs/integrations/mobile); stubs live
 * in [StubNativeCoachInference].
 */
interface NativeCoachInference {
    suspend fun scoreSession(
        sessionId: String,
        focus: String,
        driverLevel: String,
    ): LlmScoreResponseDto

    suspend fun coachAsk(
        question: String,
        driverId: String,
        sessionId: String,
    ): CoachAskResponseDto

    suspend fun coachBrief(
        driver: String?,
        focus: String?,
        sessionId: String?,
    ): BriefResponseDto
}
