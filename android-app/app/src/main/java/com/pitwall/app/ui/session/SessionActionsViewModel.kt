package com.pitwall.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitwall.app.data.remote.NetworkModule
import com.pitwall.app.data.remote.StartSessionRequest
import com.pitwall.app.di.SessionHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SessionActionsViewModel : ViewModel() {

    private val api = NetworkModule.pitwallApi

    private val _banner = MutableStateFlow<String?>(null)
    val banner: StateFlow<String?> = _banner.asStateFlow()

    fun startNewSession() {
        viewModelScope.launch {
            _banner.value = null
            try {
                val r =
                    api.startSession(
                        StartSessionRequest(
                            driver = SessionHolder.activeDriver,
                            driverLevel = "intermediate",
                            track = "Sonoma Raceway",
                            car = "BMW M3 (E46)",
                            note = "android-client",
                        ),
                    )
                SessionHolder.activeSessionId = r.sessionId
                _banner.value = "Session started: ${r.sessionId}"
            } catch (e: Exception) {
                _banner.value = e.message ?: e.toString()
            }
        }
    }
}
