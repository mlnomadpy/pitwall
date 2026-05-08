package com.pitwall.app.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitwall.app.data.remote.InsightsResponseDto
import com.pitwall.app.data.remote.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InsightsViewModel : ViewModel() {

    private val api = NetworkModule.pitwallApi

    private val _data = MutableStateFlow<InsightsResponseDto?>(null)
    val data: StateFlow<InsightsResponseDto?> = _data.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun load(lapFilter: Int? = null) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _data.value = api.insights(lap = lapFilter)
            } catch (e: Exception) {
                _error.value = e.message ?: e.toString()
                _data.value = null
            } finally {
                _loading.value = false
            }
        }
    }
}
