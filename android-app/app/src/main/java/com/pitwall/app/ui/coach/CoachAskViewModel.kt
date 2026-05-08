package com.pitwall.app.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitwall.app.data.remote.CoachAskRequestDto
import com.pitwall.app.data.remote.CoachAskStreamReader
import com.pitwall.app.data.remote.NetworkModule
import com.pitwall.app.di.SessionHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatTurn(
    val isUser: Boolean,
    val text: String,
    val emotion: String? = null,
)

class CoachAskViewModel : ViewModel() {

    private val api = NetworkModule.pitwallApi

    private val _turns = MutableStateFlow<List<ChatTurn>>(emptyList())
    val turns: StateFlow<List<ChatTurn>> = _turns.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _useStream = MutableStateFlow(false)
    val useStream: StateFlow<Boolean> = _useStream.asStateFlow()

    fun setUseStream(value: Boolean) {
        _useStream.value = value
    }

    fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty()) return
        if (_useStream.value) {
            askStreaming(q)
        } else {
            askSync(q)
        }
    }

    private fun askSync(question: String) {
        viewModelScope.launch {
            _busy.value = true
            _lastError.value = null
            _turns.value = _turns.value + ChatTurn(isUser = true, text = question)
            try {
                val r =
                    api.coachAsk(
                        CoachAskRequestDto(
                            question = question,
                            driverId = SessionHolder.activeDriver,
                            sessionId = SessionHolder.activeSessionId.orEmpty(),
                        ),
                    )
                val coachText =
                    when {
                        !r.error.isNullOrBlank() -> r.error!!
                        !r.answer.isNullOrBlank() -> r.answer!!
                        else -> "(empty response)"
                    }
                _turns.value =
                    _turns.value +
                        ChatTurn(
                            isUser = false,
                            text = coachText,
                            emotion = r.emotion,
                        )
            } catch (e: Exception) {
                val msg = e.message ?: e.toString()
                _lastError.value = msg
                _turns.value =
                    _turns.value +
                        ChatTurn(
                            isUser = false,
                            text = "Request failed: $msg",
                        )
            } finally {
                _busy.value = false
            }
        }
    }

    private fun askStreaming(question: String) {
        viewModelScope.launch {
            _busy.value = true
            _lastError.value = null
            _turns.value =
                _turns.value +
                    ChatTurn(isUser = true, text = question) +
                    ChatTurn(isUser = false, text = "")
            val body =
                CoachAskRequestDto(
                    question = question,
                    driverId = SessionHolder.activeDriver,
                    sessionId = SessionHolder.activeSessionId.orEmpty(),
                )
            try {
                withContext(Dispatchers.IO) {
                    CoachAskStreamReader.streamBlocking(
                        body = body,
                        onDelta = { d ->
                            applyDeltaToStreamingAssistant(d)
                        },
                        onDone = { answer, emotion ->
                            finalizeStreamingAssistant(answer, emotion)
                        },
                        onError = { err ->
                            _lastError.value = err
                            replaceStreamingAssistantWith(
                                "Stream failed: $err",
                                emotion = null,
                            )
                        },
                    )
                }
            } finally {
                _busy.value = false
            }
        }
    }

    private fun applyDeltaToStreamingAssistant(delta: String) {
        val list = _turns.value.toMutableList()
        val last = list.lastOrNull() ?: return
        if (!last.isUser) {
            list[list.lastIndex] = last.copy(text = last.text + delta)
            _turns.value = list
        }
    }

    private fun finalizeStreamingAssistant(answer: String, emotion: String?) {
        val list = _turns.value.toMutableList()
        val last = list.lastOrNull() ?: return
        val streamed = if (!last.isUser) last.text else ""
        val finalText = answer.ifBlank { streamed }
        if (!last.isUser) {
            list[list.lastIndex] = ChatTurn(isUser = false, text = finalText, emotion = emotion)
            _turns.value = list
        } else {
            _turns.value = list + ChatTurn(isUser = false, text = finalText, emotion = emotion)
        }
    }

    private fun replaceStreamingAssistantWith(text: String, emotion: String?) {
        val list = _turns.value.toMutableList()
        val last = list.lastOrNull() ?: run {
            _turns.value = _turns.value + ChatTurn(isUser = false, text = text, emotion = emotion)
            return
        }
        if (!last.isUser) {
            list[list.lastIndex] = ChatTurn(isUser = false, text = text, emotion = emotion)
            _turns.value = list
        } else {
            _turns.value = list + ChatTurn(isUser = false, text = text, emotion = emotion)
        }
    }

    fun clearConversation() {
        _turns.value = emptyList()
        _lastError.value = null
    }
}
