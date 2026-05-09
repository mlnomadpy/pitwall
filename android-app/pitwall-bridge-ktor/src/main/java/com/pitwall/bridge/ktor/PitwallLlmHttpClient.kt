package com.pitwall.bridge.ktor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Calls the standalone **android-llm-service** OpenAI-shaped API:
 * `POST {base}/v1/chat/completions` (see `LlmServerService`).
 */
internal object PitwallLlmHttpClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    @Serializable
    data class ChatCompletionRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val stream: Boolean = false,
        @SerialName("session_id") val sessionId: String = "pitwall-embedded",
    )

    @Serializable
    data class ChatMessage(val role: String, val content: String)

    @Serializable
    data class ChatCompletionResponse(val choices: List<ChatChoice> = emptyList())

    @Serializable
    data class ChatChoice(val message: ChatMessage? = null)

    suspend fun chatCompletion(
        baseUrl: String,
        model: String,
        sessionId: String,
        userPrompt: String,
    ): String? = withContext(Dispatchers.IO) {
        val root = baseUrl.trim().trimEnd('/')
        val url = "$root/v1/chat/completions"
        val body = ChatCompletionRequest(
            model = model,
            messages = listOf(ChatMessage("user", userPrompt)),
            stream = false,
            sessionId = sessionId,
        )
        val payload = json.encodeToString(ChatCompletionRequest.serializer(), body)
        val req = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(jsonMedia))
            .build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val text = resp.body?.string() ?: return@runCatching null
                val parsed = json.decodeFromString(ChatCompletionResponse.serializer(), text)
                parsed.choices.firstOrNull()?.message?.content?.trim()?.takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }
}
