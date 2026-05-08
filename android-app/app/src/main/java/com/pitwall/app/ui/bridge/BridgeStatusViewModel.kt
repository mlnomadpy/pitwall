package com.pitwall.app.ui.bridge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitwall.app.data.remote.HealthResponse
import com.pitwall.app.data.remote.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BridgeStatusViewModel : ViewModel() {

    private val api = NetworkModule.pitwallApi

    private val _health = MutableStateFlow<HealthResponse?>(null)
    val health: StateFlow<HealthResponse?> = _health.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun refreshHealth() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _health.value = api.health()
            } catch (e: Exception) {
                _error.value = e.message ?: e.toString()
                _health.value = null
            } finally {
                _loading.value = false
            }
        }
    }
}
