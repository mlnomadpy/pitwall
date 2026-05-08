package com.pitwall.app.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitwall.app.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

data class NotificationRow(
    /** Already formatted for display */
    val label: String,
    val rawJson: String,
)

class NotificationsViewModel : ViewModel() {

    private val client = NetworkModule.okHttpClient
    private val json = NetworkModule.serializationJson

    private var listenJob: Job? = null

    private val _rows = MutableStateFlow<List<NotificationRow>>(emptyList())
    val rows: StateFlow<List<NotificationRow>> = _rows.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    fun startStream() {
        stopStream()
        listenJob =
            viewModelScope.launch(Dispatchers.IO) {
                _connectionError.value = null
                try {
                    val request =
                        Request.Builder()
                            .url(NetworkModule.notificationsStreamUrl())
                            .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            _connectionError.value = "HTTP ${response.code}"
                            return@use
                        }
                        val body = response.body ?: return@use
                        body.byteStream().bufferedReader().use { reader ->
                            while (isActive) {
                                val line = reader.readLine() ?: break
                                val trimmed = line.trim()
                                if (!trimmed.startsWith("data:")) continue
                                val payload = trimmed.removePrefix("data:").trim()
                                if (payload.isEmpty()) continue
                                try {
                                    val obj =
                                        json.parseToJsonElement(payload).jsonObject
                                    val label = formatNotification(obj)
                                    val row =
                                        NotificationRow(
                                            label = label,
                                            rawJson =
                                                NetworkModule.serializationJson.encodeToString(
                                                    JsonObject.serializer(),
                                                    obj,
                                                ),
                                        )
                                    _rows.value = (_rows.value + row).takeLast(300)
                                } catch (_: Exception) {
                                    val row =
                                        NotificationRow(
                                            label = payload.take(120),
                                            rawJson = payload,
                                        )
                                    _rows.value = (_rows.value + row).takeLast(300)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        _connectionError.value = e.message ?: e.toString()
                    }
                }
            }
    }

    private fun formatNotification(obj: JsonObject): String {
        val kind = obj["kind"]?.jsonPrimitive?.contentOrNull ?: "event"
        val ts = obj["ts"]?.jsonPrimitive?.doubleOrNull
        val time =
            ts?.let {
                java.time.Instant.ofEpochMilli((it * 1000).toLong()).toString()
            }
                ?: "?"
        val driver = obj["driver"]?.jsonPrimitive?.contentOrNull ?: ""
        val driverSuffix = if (driver.isNotBlank() && driver != "*") " · $driver" else ""
        return "$time · $kind$driverSuffix"
    }

    fun stopStream() {
        listenJob?.cancel()
        listenJob = null
    }

    fun clearLog() {
        _rows.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        stopStream()
    }
}
