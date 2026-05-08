package com.pitwall.app.data.remote

import com.pitwall.app.BuildConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.text.Charsets
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * POST /coach/ask/stream — parses SSE `data: {...}` lines (delta / done / error).
 */
object CoachAskStreamReader {

    fun streamBlocking(
        body: CoachAskRequestDto,
        onDelta: (String) -> Unit,
        onDone: (answer: String, emotion: String?) -> Unit,
        onError: (String) -> Unit,
    ) {
        val json = NetworkModule.serializationJson
        val url = BuildConfig.PITWALL_API_BASE_URL.trimEnd('/') + "/coach/ask/stream"
        val payload =
            json.encodeToString(
                CoachAskRequestDto.serializer(),
                body,
            )
        val req =
            Request.Builder()
                .url(url)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
        NetworkModule.okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val snippet =
                    try {
                        resp.body?.string()?.trim()?.take(400)
                    } catch (_: Exception) {
                        null
                    }
                onError(
                    buildString {
                        append("HTTP ${resp.code}")
                        if (!snippet.isNullOrBlank()) {
                            append(": ")
                            append(snippet)
                        }
                    },
                )
                return
            }
            resp.body?.byteStream()?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                reader.forEachLine { raw ->
                    val line = raw.trim()
                    if (!line.startsWith("data:")) return@forEachLine
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) return@forEachLine
                    try {
                        val obj = json.parseToJsonElement(data).jsonObject
                        parseChunk(obj, onDelta, onDone, onError)
                    } catch (e: Exception) {
                        onError("parse: ${e.message}")
                    }
                }
            } ?: onError("empty body")
        }
    }

    private fun parseChunk(
        obj: JsonObject,
        onDelta: (String) -> Unit,
        onDone: (String, String?) -> Unit,
        onError: (String) -> Unit,
    ) {
        obj["error"]?.jsonPrimitive?.contentOrNull?.let {
            onError(it)
            return
        }
        obj["delta"]?.jsonPrimitive?.contentOrNull?.let(onDelta)
        val done =
            obj["done"]?.jsonPrimitive?.booleanOrNull
                ?: (obj["done"]?.jsonPrimitive?.contentOrNull == "true")
        if (done) {
            val answer = obj["answer"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val emotion = obj["emotion"]?.jsonPrimitive?.contentOrNull
            onDone(answer, emotion)
        }
    }
}
