package com.pitwall.app.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitwall.app.data.remote.CoachConceptsResponse
import com.pitwall.app.data.remote.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CoachConceptsViewModel : ViewModel() {

    private val api = NetworkModule.pitwallApi

    private val _data = MutableStateFlow<CoachConceptsResponse?>(null)
    val data: StateFlow<CoachConceptsResponse?> = _data.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _data.value = api.coachConcepts()
            } catch (e: Exception) {
                _error.value = e.message ?: e.toString()
                _data.value = null
            } finally {
                _loading.value = false
            }
        }
    }
}
