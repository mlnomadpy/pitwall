package com.pitwall.app.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitwall.app.data.remote.IdealLapDto
import com.pitwall.app.data.remote.LapTimeTableDto
import com.pitwall.app.data.remote.NetworkModule
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LapTimesViewModel : ViewModel() {

    private val api = NetworkModule.pitwallApi

    private val _table = MutableStateFlow<LapTimeTableDto?>(null)
    val table: StateFlow<LapTimeTableDto?> = _table.asStateFlow()

    private val _ideal = MutableStateFlow<IdealLapDto?>(null)
    val ideal: StateFlow<IdealLapDto?> = _ideal.asStateFlow()

    private val _idealError = MutableStateFlow<String?>(null)
    val idealError: StateFlow<String?> = _idealError.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun load(sessionId: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _table.value = api.lapTimeTable(sessionId)
            } catch (e: Exception) {
                _error.value = e.message ?: e.toString()
                _table.value = null
            } finally {
                _loading.value = false
            }
            launch {
                try {
                    _ideal.value = api.idealLap(sessionId)
                    _idealError.value = null
                } catch (e: Exception) {
                    _ideal.value = null
                    _idealError.value = e.message ?: e.toString()
                }
            }
        }
    }
}
