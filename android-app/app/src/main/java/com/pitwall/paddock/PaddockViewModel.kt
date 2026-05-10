package com.pitwall.paddock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitwall.paddock.data.AnalyzeBurstRequest
import com.pitwall.paddock.data.NetworkModule
import com.pitwall.paddock.data.PitwallApi
import com.pitwall.paddock.data.TrackMarker
import com.pitwall.paddock.data.MockSonomaData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaddockUiState(
    val bridgeLine: String = "Tap refresh to reach pitwall bridge",
    val analyzePreview: String = "",
    val selectedMarkerIds: Set<String> = emptySet(),
)

class PaddockViewModel(
    private val api: PitwallApi = NetworkModule.api,
) : ViewModel() {

    private val _state = MutableStateFlow(PaddockUiState())
    val state: StateFlow<PaddockUiState> = _state.asStateFlow()

    init {
        refreshBridgeHealth()
    }

    fun refreshBridgeHealth() {
        viewModelScope.launch {
            _state.update {
                it.copy(bridgeLine = "Checking bridge…")
            }
            _state.update {
                try {
                    val h = api.health()
                    it.copy(
                        bridgeLine = "v${h.version} · ${h.engine} · ${h.track ?: "—"}",
                    )
                } catch (e: Exception) {
                    it.copy(bridgeLine = "Offline: ${e.message?.take(80)}")
                }
            }
        }
    }

    /** Vertical slice: POST /analyze with a minimal burst (embedded Ktor or Python bridge). */
    fun runAnalyzeDemo() {
        viewModelScope.launch {
            _state.update { it.copy(analyzePreview = "Calling /analyze…") }
            _state.update {
                try {
                    val r = api.analyze(AnalyzeBurstRequest())
                    it.copy(analyzePreview = r.coaching.take(400))
                } catch (e: Exception) {
                    it.copy(analyzePreview = "/analyze failed: ${e.message?.take(200)}")
                }
            }
        }
    }

    fun toggleFocus(markerId: String) {
        _state.update { s ->
            val next = s.selectedMarkerIds.toMutableSet()
            if (next.contains(markerId)) {
                next.remove(markerId)
            } else if (next.size < 3) {
                next.add(markerId)
            }
            s.copy(selectedMarkerIds = next)
        }
    }

    fun clearFocus() {
        _state.update { it.copy(selectedMarkerIds = emptySet()) }
    }

    val markers: List<TrackMarker> get() = MockSonomaData.markers
}
