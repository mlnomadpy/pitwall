package com.pitwall.app.ui.bridge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitwall.app.data.remote.HealthResponse
import com.pitwall.app.data.remote.NetworkModule
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BridgeStatusViewModel : ViewModel() {

    private val api = NetworkModule.pitwallApi

    private val pollMutex = Mutex()

    private val _health = MutableStateFlow<HealthResponse?>(null)
    val health: StateFlow<HealthResponse?> = _health.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Mirrors PWA [bridgeStore] — bumps on each failed GET /health while polling. */
    private val _consecutiveFailures = MutableStateFlow(0)
    val consecutiveFailures: StateFlow<Int> = _consecutiveFailures.asStateFlow()

    private val _healthFetchedAt = MutableStateFlow(0L)
    val healthFetchedAt: StateFlow<Long> = _healthFetchedAt.asStateFlow()

    private val _isPolling = MutableStateFlow(false)
    val isPolling: StateFlow<Boolean> = _isPolling.asStateFlow()

    private var pollJob: Job? = null

    /** Manual ping from Title screen — shows [loading] on the button. */
    fun refreshHealth() {
        viewModelScope.launch {
            _loading.value = true
            try {
                pollMutex.withLock {
                    runPollAttempt()
                }
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Starts background polling every 5s (same cadence as [src/pwa/src/shared/api/bridgeStore.ts]).
     * Idempotent while active.
     */
    fun startPolling() {
        if (pollJob?.isActive == true) return
        _isPolling.value = true
        pollJob =
            viewModelScope.launch {
                while (isActive) {
                    pollMutex.withLock {
                        runPollAttempt()
                    }
                    delay(POLL_INTERVAL_MS)
                }
            }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        _isPolling.value = false
    }

    private fun runPollAttempt() {
        try {
            _health.value = api.health()
            _healthFetchedAt.value = System.currentTimeMillis()
            _error.value = null
            _consecutiveFailures.value = 0
        } catch (e: Exception) {
            _error.value = e.message ?: e.toString()
            _health.value = null
            _consecutiveFailures.value = _consecutiveFailures.value + 1
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
    }
}
