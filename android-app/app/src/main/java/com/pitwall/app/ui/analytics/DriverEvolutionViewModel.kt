package com.pitwall.app.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitwall.app.data.remote.NetworkModule
import com.pitwall.app.di.SessionHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DriverEvolutionViewModel : ViewModel() {

    private val api = NetworkModule.pitwallApi

    private val _evolutionJson = MutableStateFlow<JsonObject?>(null)
    val evolutionJson: StateFlow<JsonObject?> = _evolutionJson.asStateFlow()

    private val _profileJson = MutableStateFlow<JsonObject?>(null)
    val profileJson: StateFlow<JsonObject?> = _profileJson.asStateFlow()

    private val _lapTrend = MutableStateFlow<List<Double>>(emptyList())
    val lapTrend: StateFlow<List<Double>> = _lapTrend.asStateFlow()

    private val _pbSparkline = MutableStateFlow<List<Double>>(emptyList())
    val pbSparkline: StateFlow<List<Double>> = _pbSparkline.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun refresh() {
        val driver = SessionHolder.activeDriver.trim().ifEmpty { return }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val evo = api.driverEvolution(driverId = driver, track = null)
                _evolutionJson.value = evo
                _lapTrend.value = extractEvolutionBestLaps(evo)
                val prof =
                    runCatching {
                        api.driverProfile(driver)
                    }.getOrNull()
                _profileJson.value = prof
                _pbSparkline.value = prof?.let(::extractPbHistory) ?: emptyList()
            } catch (e: Exception) {
                _error.value = e.message ?: e.toString()
                _evolutionJson.value = null
                _lapTrend.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }

    private fun extractEvolutionBestLaps(json: JsonObject): List<Double> {
        val arr = json["evolution"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            el.jsonObject["best_lap_s"]?.jsonPrimitive?.doubleOrNull
        }
    }

    private fun extractPbHistory(json: JsonObject): List<Double> {
        val arr = json["best_lap_history"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            el.jsonObject["lap_s"]?.jsonPrimitive?.doubleOrNull
        }
    }
}
