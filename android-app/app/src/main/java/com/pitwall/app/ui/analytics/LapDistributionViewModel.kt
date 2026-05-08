package com.pitwall.app.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitwall.app.data.remote.LapTimeDistributionDto
import com.pitwall.app.data.remote.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LapDistributionViewModel : ViewModel() {

    private val api = NetworkModule.pitwallApi

    private val _dist = MutableStateFlow<LapTimeDistributionDto?>(null)
    val dist: StateFlow<LapTimeDistributionDto?> = _dist.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun load(sessionId: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _dist.value = api.lapTimeDistribution(sessionId)
            } catch (e: Exception) {
                _error.value = e.message ?: e.toString()
                _dist.value = null
            } finally {
                _loading.value = false
            }
        }
    }
}
