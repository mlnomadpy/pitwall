package com.pitwall.app.ui.sessions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pitwall.app.data.remote.NetworkModule
import com.pitwall.app.data.remote.SessionDetailDto
import com.pitwall.app.data.remote.prettyJson
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.ResponseBody

class SessionDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val api = NetworkModule.pitwallApi

    private val _detail = MutableStateFlow<SessionDetailDto?>(null)
    val detail: StateFlow<SessionDetailDto?> = _detail.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _bridgeBusy = MutableStateFlow(false)
    val bridgeBusy: StateFlow<Boolean> = _bridgeBusy.asStateFlow()

    private val _bridgeMessage = MutableStateFlow<String?>(null)
    val bridgeMessage: StateFlow<String?> = _bridgeMessage.asStateFlow()

    fun load(sessionId: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _detail.value = api.getSession(sessionId)
            } catch (e: Exception) {
                _error.value = e.message ?: e.toString()
                _detail.value = null
            } finally {
                _loading.value = false
            }
        }
    }

    /** Large payloads — preview head + length hint. */
    fun fetchSyncPreview(sessionId: String) {
        viewModelScope.launch {
            _bridgeBusy.value = true
            try {
                val json = api.sessionSync(sessionId)
                val pretty = json.prettyJson()
                _bridgeMessage.value =
                    if (pretty.length > 12_000) {
                        pretty.take(12_000) + "\n… (${pretty.length} chars total)"
                    } else {
                        pretty
                    }
            } catch (e: Exception) {
                _bridgeMessage.value = e.message ?: e.toString()
            } finally {
                _bridgeBusy.value = false
            }
        }
    }

    fun exportParquet(sessionId: String, table: String? = null) {
        viewModelScope.launch {
            _bridgeBusy.value = true
            try {
                val body: ResponseBody = api.sessionExportParquet(sessionId, table)
                val dir = File(getApplication<Application>().cacheDir, "exports")
                dir.mkdirs()
                val safeTable = (table ?: "telemetry").replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val file = File(dir, "${sessionId}_$safeTable.parquet")
                body.byteStream().use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                _bridgeMessage.value = "Saved Parquet\n${file.absolutePath}"
            } catch (e: Exception) {
                _bridgeMessage.value = e.message ?: e.toString()
            } finally {
                _bridgeBusy.value = false
            }
        }
    }

    fun resetBursts() {
        viewModelScope.launch {
            _bridgeBusy.value = true
            try {
                val j = api.sessionReset()
                _bridgeMessage.value = j.prettyJson()
            } catch (e: Exception) {
                _bridgeMessage.value = e.message ?: e.toString()
            } finally {
                _bridgeBusy.value = false
            }
        }
    }
}
