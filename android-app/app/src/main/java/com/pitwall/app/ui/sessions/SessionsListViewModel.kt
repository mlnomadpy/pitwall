package com.pitwall.app.ui.sessions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pitwall.app.data.local.SaveStore
import com.pitwall.app.data.remote.NetworkModule
import com.pitwall.app.data.remote.SessionSummaryDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SessionsListViewModel(application: Application) : AndroidViewModel(application) {

    private val api = NetworkModule.pitwallApi

    private val _sessions = MutableStateFlow<List<SessionSummaryDto>>(emptyList())
    val sessions: StateFlow<List<SessionSummaryDto>> = _sessions.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun load(limit: Int = 50) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val env = api.sessions(limit = limit, activeOnly = false)
                _sessions.value = env.sessions
                SaveStore.mergeSessionsFromBridge(getApplication(), env.sessions)
            } catch (e: Exception) {
                _error.value = e.message ?: e.toString()
            } finally {
                _loading.value = false
            }
        }
    }
}
