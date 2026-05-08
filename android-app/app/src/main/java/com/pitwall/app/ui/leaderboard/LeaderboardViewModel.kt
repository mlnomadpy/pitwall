package com.pitwall.app.ui.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitwall.app.data.remote.NetworkModule
import com.pitwall.app.di.SessionHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class LeaderboardSource {
    Bridge,
    Mock,
}

data class LeaderboardEntryUi(
    val rank: Int,
    val name: String,
    val carOrMeta: String,
    val trackOrSession: String,
    val timeText: String,
    val isHighlighted: Boolean,
)

class LeaderboardViewModel : ViewModel() {

    private val api = NetworkModule.pitwallApi

    private val _entries = MutableStateFlow<List<LeaderboardEntryUi>>(emptyList())
    val entries: StateFlow<List<LeaderboardEntryUi>> = _entries.asStateFlow()

    private val _source = MutableStateFlow(LeaderboardSource.Mock)
    val source: StateFlow<LeaderboardSource> = _source.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _errorHint = MutableStateFlow<String?>(null)
    val errorHint: StateFlow<String?> = _errorHint.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _errorHint.value = null
            try {
                val sessionsEnv =
                    runCatching {
                        api.sessions(limit = 80, activeOnly = false)
                    }.getOrNull()
                val lapsJson =
                    runCatching {
                        api.laps(sessionId = null, limit = 120)
                    }.getOrNull()

                val sidToDriver =
                    sessionsEnv?.sessions?.associate {
                        it.sessionId to it.driver
                    }.orEmpty()
                val sidToTrack =
                    sessionsEnv?.sessions?.associate {
                        it.sessionId to it.track
                    }.orEmpty()
                val sidToCar =
                    sessionsEnv?.sessions?.associate {
                        it.sessionId to it.car
                    }.orEmpty()

                val lapsArr = lapsJson?.get("laps")?.jsonArray
                if (!lapsArr.isNullOrEmpty()) {
                    val parsed: List<Triple<String, Double, Int?>> =
                        lapsArr.mapNotNull { el ->
                            val o = el.jsonObject
                            val sid =
                                o["session_id"]?.jsonPrimitive?.contentOrNull
                                    ?: return@mapNotNull null
                            val t =
                                o["lap_time_s"]?.jsonPrimitive?.doubleOrNull
                                    ?: return@mapNotNull null
                            val lapN =
                                o["lap_number"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                            Triple(sid, t, lapN)
                        }
                    if (parsed.isNotEmpty()) {
                        val bestBySession: Map<String, Triple<String, Double, Int?>> =
                            parsed
                                .groupBy { trip -> trip.first }
                                .mapValues { (_, lapsForSid) ->
                                    lapsForSid.minByOrNull { trip -> trip.second }!!
                                }
                        val ordered: List<Triple<String, Double, Int?>> =
                            bestBySession.values
                                .sortedBy { trip -> trip.second }
                                .take(25)
                        val active = SessionHolder.activeSessionId
                        _entries.value =
                            ordered.mapIndexed { i, tr ->
                                val sid = tr.first
                                val driver = sidToDriver[sid] ?: sid.take(8)
                                val track = sidToTrack[sid] ?: "—"
                                val car = sidToCar[sid] ?: "—"
                                val lapPart = tr.third?.let { n -> "L$n" } ?: "lap"
                                LeaderboardEntryUi(
                                    rank = i + 1,
                                    name = driver,
                                    carOrMeta = car,
                                    trackOrSession = "$track · $lapPart",
                                    timeText = formatLapTime(tr.second),
                                    isHighlighted = !active.isNullOrBlank() && active == sid,
                                )
                            }
                        _source.value = LeaderboardSource.Bridge
                        return@launch
                    }
                }
            } catch (e: Exception) {
                _errorHint.value = e.message?.take(120)
            } finally {
                _loading.value = false
            }
            showMock()
        }
    }

    private fun showMock() {
        _source.value = LeaderboardSource.Mock
        _entries.value =
            listOf(
                LeaderboardEntryUi(1, "TRD", "GT3_911", "SONOMA", "1:34.210", false),
                LeaderboardEntryUi(2, "BTY", "M4_GT3", "SONOMA", "1:35.050", false),
                LeaderboardEntryUi(3, "DRL", "AMG_GT3", "SONOMA", "1:35.800", false),
                LeaderboardEntryUi(4, "YOU", "GT3_911", "SONOMA", "1:36.450", true),
                LeaderboardEntryUi(5, "CLM", "720S_GT3", "SONOMA", "1:37.110", false),
                LeaderboardEntryUi(6, "BDY", "M4_GT3", "SONOMA", "1:38.000", false),
                LeaderboardEntryUi(7, "AI1", "GT3_911", "SONOMA", "1:38.500", false),
                LeaderboardEntryUi(8, "AI2", "AMG_GT3", "SONOMA", "1:39.100", false),
            )
    }

    private fun formatLapTime(seconds: Double): String {
        if (seconds <= 0 || seconds.isNaN()) return "—"
        val m = (seconds / 60).toInt()
        val s = seconds % 60
        return "%d:%06.3f".format(m, s)
    }
}
