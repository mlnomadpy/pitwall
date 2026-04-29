package com.pitwall.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pitwall.app.data.CoachingMessage
import com.pitwall.app.data.TelemetryFrame
import com.pitwall.app.service.PitwallService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppMode { SETUP, ON_TRACK, PADDOCK }

data class PitwallUiState(
    val mode: AppMode = AppMode.SETUP,
    val telemetry: TelemetryFrame? = null,
    val lastCoaching: CoachingMessage? = null,
    val driverLevel: String = "intermediate",
    val isStarting: Boolean = false,
    val slowSinceMs: Long? = null,
)

/**
 * Sealed event that MainActivity observes to perform Activity-context operations
 * (starting foreground service, binding, unbinding).
 * ViewModel never holds an Activity or service reference directly.
 */
sealed class SessionCommand {
    data class Start(val replayPath: String?, val level: String) : SessionCommand()
    object Stop : SessionCommand()
}

class PitwallViewModel(application: Application) : AndroidViewModel(application) {

    private val _ui = MutableStateFlow(PitwallUiState())
    val ui: StateFlow<PitwallUiState> = _ui.asStateFlow()

    /** MainActivity collects this to execute Activity-context service calls. */
    private val _commands = MutableSharedFlow<SessionCommand>(extraBufferCapacity = 8)
    val commands: SharedFlow<SessionCommand> = _commands.asSharedFlow()

    private var service: PitwallService? = null
    private var telemetryJob: Job? = null
    private var coachingJob: Job? = null

    // ── Called by MainActivity after service bind ─────────────────────────────

    fun onServiceConnected(svc: PitwallService) {
        service = svc
        telemetryJob?.cancel()
        coachingJob?.cancel()
        telemetryJob = viewModelScope.launch {
            svc.telemetry.collect { frame ->
                if (frame == null) return@collect
                _ui.update { it.copy(telemetry = frame) }
                checkAutoTransition(frame)
            }
        }
        coachingJob = viewModelScope.launch {
            svc.coaching.collect { msg ->
                _ui.update { it.copy(lastCoaching = msg) }
            }
        }
    }

    fun onServiceDisconnected() {
        service = null
        telemetryJob?.cancel()
        coachingJob?.cancel()
    }

    // ── UI actions ────────────────────────────────────────────────────────────

    fun startSession(replayPath: String?, level: String) {
        _ui.update { it.copy(isStarting = true, driverLevel = level) }
        viewModelScope.launch {
            _commands.emit(SessionCommand.Start(replayPath, level))
        }
    }

    fun onSessionStarted() {
        _ui.update { it.copy(isStarting = false, mode = AppMode.ON_TRACK) }
    }

    fun stopSession() {
        viewModelScope.launch { _commands.emit(SessionCommand.Stop) }
        telemetryJob?.cancel()
        coachingJob?.cancel()
        _ui.update { PitwallUiState() }
    }

    fun enterPaddock() = _ui.update { it.copy(mode = AppMode.PADDOCK) }
    fun returnToTrack() = _ui.update { it.copy(mode = AppMode.ON_TRACK) }
    fun setDriverLevel(level: String) = _ui.update { it.copy(driverLevel = level) }

    // ── Auto-transition ───────────────────────────────────────────────────────

    private fun checkAutoTransition(frame: TelemetryFrame) {
        val now = System.currentTimeMillis()
        val mode = _ui.value.mode
        when {
            frame.speedMph < 5f && mode == AppMode.ON_TRACK -> {
                val since = _ui.value.slowSinceMs ?: now
                _ui.update { it.copy(slowSinceMs = since) }
                if (now - since >= 30_000L) enterPaddock()
            }
            frame.speedMph > 20f && mode == AppMode.PADDOCK -> {
                _ui.update { it.copy(slowSinceMs = null) }
                returnToTrack()
            }
            else -> _ui.update { it.copy(slowSinceMs = null) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        telemetryJob?.cancel()
        coachingJob?.cancel()
    }
}
