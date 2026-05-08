package com.pitwall.app.ui.hud

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
import okhttp3.Request

class HudViewModel : ViewModel() {

    private val client = NetworkModule.okHttpClient
    private fun streamUrl(): String = NetworkModule.cuesStreamUrl()

    private var listenJob: Job? = null

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    fun startCueStream() {
        stopCueStream()
        listenJob =
            viewModelScope.launch(Dispatchers.IO) {
                _connectionError.value = null
                try {
                    val request = Request.Builder().url(streamUrl()).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            _connectionError.value = "HTTP ${response.code}"
                            return@use
                        }
                        val body = response.body ?: return@use
                        body.byteStream().bufferedReader().use { reader ->
                            while (isActive) {
                                val line = reader.readLine() ?: break
                                _lines.value = (_lines.value + line).takeLast(200)
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

    fun stopCueStream() {
        listenJob?.cancel()
        listenJob = null
    }

    fun clearLog() {
        _lines.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        stopCueStream()
    }
}
