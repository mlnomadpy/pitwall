package com.pitwall.llmservice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.pm.ServiceInfo
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.serialization.gson.*
import io.ktor.server.request.*
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.http.CacheControl
import io.ktor.utils.io.*
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean = false,
    @SerializedName("session_id") val sessionId: String = "default",
)

data class Message(
    val role: String,
    val content: String
)

data class ChatResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>
)

data class Choice(
    val index: Int,
    val message: Message,
    @SerializedName("finish_reason") val finishReason: String
)

data class StreamResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<StreamChoice>
)

data class StreamChoice(
    val index: Int,
    val delta: StreamDelta,
    @SerializedName("finish_reason") val finishReason: String?
)

data class StreamDelta(
    val role: String? = null,
    val content: String? = null
)

data class ErrorResponse(
    val error: ErrorDetails
)

data class ErrorDetails(
    val message: String,
    val type: String,
    val code: Int
)

class LlmServerService : Service() {
    private var server: NettyApplicationEngine? = null
    private val sessions = ConcurrentHashMap<String, LlmInference>()
    private val tpuMutex = Mutex()
    private val modelPath = "/sdcard/Pitwall/models/gemma-4-E2B-it.task"
    private val gson = Gson()
    
    // Global callback for the active inference
    private var activeStreamCallback: ((String, Boolean) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        startForeground()
        startServer()
    }

    private fun startForeground() {
        val channelId = "llm_service_channel"
        val channelName = "LLM Background Service"
        val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_NONE)
        val manager = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
        manager.createNotificationChannel(chan)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
        val notification = notificationBuilder.setOngoing(true)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Pitwall LLM Server")
            .setContentText("Running on port 8080")
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun formatGemmaPrompt(messages: List<Message>): String {
        val sb = StringBuilder()
        for (msg in messages) {
            val role = if (msg.role == "system") "user" else msg.role
            sb.append("<start_of_turn>$role\n")
            sb.append(msg.content)
            sb.append("<end_of_turn>\n")
        }
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    private fun startServer() {
        server = embeddedServer(Netty, port = 8080, host = "127.0.0.1") {
            install(ContentNegotiation) { gson() }
            install(CORS) { anyHost() }
            
            routing {
                get("/health") {
                    call.respond(mapOf("status" to "ok", "service" to "pitwall-llm-android"))
                }

                post("/v1/chat/completions") {
                    val req = call.receive<ChatRequest>()
                    
                    try {
                        val engine = getOrCreateEngine(req.sessionId)
                        val formattedPrompt = formatGemmaPrompt(req.messages)
                        val responseId = "chatcmpl-${System.currentTimeMillis()}"
                        
                        if (req.stream) {
                            call.response.cacheControl(CacheControl.NoCache(null))
                            call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                                tpuMutex.withLock {
                                    val flow = callbackFlow {
                                        activeStreamCallback = { partialResult, done ->
                                            trySend(Pair(partialResult, done))
                                            if (done) close()
                                        }
                                        engine.generateResponseAsync(formattedPrompt)
                                        awaitClose { activeStreamCallback = null }
                                    }

                                    val initResp = StreamResponse(
                                        id = responseId,
                                        `object` = "chat.completion.chunk",
                                        created = System.currentTimeMillis() / 1000,
                                        model = req.model,
                                        choices = listOf(StreamChoice(0, StreamDelta(role = "assistant"), null))
                                    )
                                    writeStringUtf8("data: ${gson.toJson(initResp)}\n\n")
                                    flush()

                                    flow.collect { (chunk, done) ->
                                        if (chunk.isNotEmpty()) {
                                            val chunkResp = StreamResponse(
                                                id = responseId,
                                                `object` = "chat.completion.chunk",
                                                created = System.currentTimeMillis() / 1000,
                                                model = req.model,
                                                choices = listOf(StreamChoice(0, StreamDelta(content = chunk), if (done) "stop" else null))
                                            )
                                            writeStringUtf8("data: ${gson.toJson(chunkResp)}\n\n")
                                            flush()
                                        }
                                    }

                                    writeStringUtf8("data: [DONE]\n\n")
                                    flush()
                                }
                            }
                        } else {
                            val responseText = tpuMutex.withLock {
                                withContext(Dispatchers.Default) {
                                    engine.generateResponse(formattedPrompt)
                                }
                            }
                            
                            val resp = ChatResponse(
                                id = responseId,
                                `object` = "chat.completion",
                                created = System.currentTimeMillis() / 1000,
                                model = req.model,
                                choices = listOf(
                                    Choice(
                                        index = 0,
                                        message = Message(role = "assistant", content = responseText),
                                        finishReason = "stop"
                                    )
                                )
                            )
                            call.respond(resp)
                        }
                    } catch (e: Exception) {
                        Log.e("LlmServerService", "Inference error", e)
                        val err = ErrorResponse(
                            ErrorDetails(
                                message = e.message ?: "Unknown error",
                                type = "server_error",
                                code = 500
                            )
                        )
                        call.respond(HttpStatusCode.InternalServerError, err)
                    }
                }
            }
        }.start(wait = false)
    }
    
    private fun getOrCreateEngine(sessionId: String): LlmInference {
        return sessions.getOrPut(sessionId) {
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                throw IllegalStateException("Model file not found at $modelPath")
            }
            
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .setResultListener { result, done ->
                    activeStreamCallback?.invoke(result, done)
                }
                .build()
                
            LlmInference.createFromOptions(this, options)
        }
    }

    override fun onDestroy() {
        server?.stop(1000, 2000)
        sessions.values.forEach { it.close() }
        sessions.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
