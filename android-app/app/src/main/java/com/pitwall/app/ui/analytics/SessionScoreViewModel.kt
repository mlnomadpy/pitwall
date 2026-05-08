package com.pitwall.app.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitwall.app.data.local.SaveStore
import com.pitwall.app.data.remote.LlmScoreRequestDto
import com.pitwall.app.data.remote.LlmScoreResponseDto
import com.pitwall.app.data.remote.NetworkModule
import com.pitwall.app.di.SessionHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SessionScoreViewModel : ViewModel() {

    private val api = NetworkModule.pitwallApi

    private val _result = MutableStateFlow<LlmScoreResponseDto?>(null)
    val result: StateFlow<LlmScoreResponseDto?> = _result.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun gradeSession(focus: String) {
        val sid = SessionHolder.activeSessionId?.trim().orEmpty()
        if (sid.isEmpty()) {
            _error.value = "Select an active session first (Bridge sessions)."
            _result.value = null
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _result.value = null
            try {
                val level = SaveStore.activeSlot()?.skillLevel?.trim()?.ifEmpty { null } ?: "intermediate"
                _result.value =
                    api.scoreSession(
                        LlmScoreRequestDto(
                            sessionId = sid,
                            focus = focus.trim(),
                            driverLevel = level,
                        ),
                    )
            } catch (e: Exception) {
                _error.value = e.message ?: e.toString()
            } finally {
                _loading.value = false
            }
        }
    }

}
