package com.pitwall.paddock

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitwall.paddock.data.BridgeClient
import com.pitwall.paddock.data.CornerSessionStats
import com.pitwall.paddock.data.DriverInsight
import com.pitwall.paddock.data.MockSonomaData
import com.pitwall.paddock.data.NetworkModule
import com.pitwall.paddock.data.PitwallApi
import com.pitwall.paddock.data.TrackMarker
import com.pitwall.paddock.data.TrackOutline
import com.pitwall.paddock.data.SaveSlot
import com.pitwall.paddock.data.SaveSlotStore
import com.pitwall.paddock.data.BriefResponse
import com.pitwall.paddock.data.SessionSummary
import com.pitwall.paddock.data.LapTimeTableResponse
import com.pitwall.paddock.data.ScorecardResponse
import com.pitwall.paddock.data.PedalBehaviorResponse
import com.pitwall.paddock.data.TelemetryFrame
import com.pitwall.paddock.data.CueEvent
import com.pitwall.paddock.data.StartSessionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Lap record (lightweight — mirrors android/service/LapRecord) ──────────────
data class LapRecord(
    val lap: Int,
    val timeS: Float,
    val bestDeltaS: Float = 0f,
)

// ── UI State ──────────────────────────────────────────────────────────────────
data class PaddockUiState(
    // Existing state
    val bridgeLine: String = "Tap refresh to reach pitwall bridge",
    val bridgeOnline: Boolean = false,
    val selectedMarkerIds: Set<String> = emptySet(),
    
    // Save slot
    val activeSlot: SaveSlot? = null,
    
    // Analysis data (from data layer migration)
    val laps: List<LapRecord> = emptyList(),
    val insights: List<DriverInsight> = emptyList(),
    val insightsLoading: Boolean = false,
    val insightsError: Boolean = false,
    val trackOutline: TrackOutline? = null,
    val cornerStats: Map<String, CornerSessionStats> = emptyMap(),
    val useMph: Boolean = false,
    
    // Expanded PWA Parity State
    val activeSessionId: String? = null,
    val briefResponse: BriefResponse? = null,
    val briefLoading: Boolean = false,
    val sessions: List<SessionSummary> = emptyList(),
    val currentLapTimeTable: LapTimeTableResponse? = null,
    val currentScorecard: ScorecardResponse? = null,
    val currentPedalBehavior: PedalBehaviorResponse? = null,
    
    // Streams
    val telemetryFrame: TelemetryFrame? = null,
    val activeCue: CueEvent? = null,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────
class PaddockViewModel(
    private val api: PitwallApi = NetworkModule.api,
    private val bridge: BridgeClient = BridgeClient(),
) : ViewModel() {

    private val _state = MutableStateFlow(PaddockUiState())
    val state: StateFlow<PaddockUiState> = _state.asStateFlow()

    private val fetchedLaps = mutableSetOf<Int>()
    
    private var telemetryJob: Job? = null
    private var cueJob: Job? = null

    init {
        refreshBridgeHealth()
    }
    
    // ── Save Slot Management ──────────────────────────────────────────────────
    fun loadSaveSlot(context: Context) {
        viewModelScope.launch {
            SaveSlotStore.getActiveSlot(context).collect { slot ->
                _state.update { it.copy(activeSlot = slot) }
            }
        }
    }
    
    fun createSaveSlot(context: Context, slot: SaveSlot) {
        viewModelScope.launch {
            SaveSlotStore.saveActiveSlot(context, slot)
        }
    }
    
    fun clearSaveSlot(context: Context) {
        viewModelScope.launch {
            SaveSlotStore.clearActiveSlot(context)
        }
    }

    // ── Bridge & Session ──────────────────────────────────────────────────────
    fun refreshBridgeHealth() {
        viewModelScope.launch {
            _state.update { it.copy(bridgeLine = "Checking bridge…", bridgeOnline = false) }
            _state.update {
                try {
                    val h = api.health()
                    it.copy(
                        bridgeLine = "v${h.version} · ${h.engine} · ${h.track ?: "—"}",
                        bridgeOnline = true
                    )
                } catch (e: Exception) {
                    it.copy(
                        bridgeLine = "Offline: ${e.message?.take(80)}",
                        bridgeOnline = false
                    )
                }
            }
        }
    }
    
    fun startSession(req: StartSessionRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val res = api.startSession(req)
                if (res.started) {
                    _state.update { it.copy(activeSessionId = res.sessionId) }
                    onSuccess()
                }
            } catch (e: Exception) {
                android.util.Log.e("PaddockViewModel", "Failed to start session", e)
            }
        }
    }
    
    fun endSession(onSuccess: () -> Unit) {
        val sid = _state.value.activeSessionId ?: return
        viewModelScope.launch {
            try {
                api.endSession(sid)
                closeStreams()
                _state.update { it.copy(activeSessionId = null) }
                onSuccess()
            } catch (e: Exception) {
                android.util.Log.e("PaddockViewModel", "Failed to end session", e)
            }
        }
    }

    // ── Pre-Brief & Feedback ──────────────────────────────────────────────────
    fun fetchBrief(driver: String, track: String = "sonoma") {
        viewModelScope.launch {
            _state.update { it.copy(briefLoading = true) }
            try {
                val brief = api.brief(driver = driver, track = track)
                _state.update { it.copy(briefResponse = brief, briefLoading = false) }
            } catch (e: Exception) {
                android.util.Log.e("PaddockViewModel", "Failed to fetch brief", e)
                _state.update { it.copy(briefLoading = false) }
            }
        }
    }

    // ── Live Telemetry & Cues (SSE) ───────────────────────────────────────────
    fun openStreams(sid: String) {
        telemetryJob?.cancel()
        cueJob?.cancel()
        
        telemetryJob = viewModelScope.launch {
            bridge.telemetryStream(sid).collect { frame ->
                _state.update { it.copy(telemetryFrame = frame) }
            }
        }
        
        cueJob = viewModelScope.launch {
            bridge.cueStream(sid).collect { cue ->
                _state.update { it.copy(activeCue = cue) }
                // Clear cue after 3 seconds
                kotlinx.coroutines.delay(3000)
                _state.update { if (it.activeCue == cue) it.copy(activeCue = null) else it }
            }
        }
    }
    
    fun closeStreams() {
        telemetryJob?.cancel()
        telemetryJob = null
        cueJob?.cancel()
        cueJob = null
        _state.update { it.copy(telemetryFrame = null, activeCue = null) }
    }

    // ── Post-Session Analysis ─────────────────────────────────────────────────
    fun fetchSessions() {
        viewModelScope.launch {
            try {
                val list = api.sessions(limit = 100)
                _state.update { it.copy(sessions = list.sessions) }
            } catch (e: Exception) {
                android.util.Log.e("PaddockViewModel", "Failed to fetch sessions", e)
            }
        }
    }
    
    fun fetchLapTimeTable(sid: String) {
        viewModelScope.launch {
            try {
                val table = api.lapTimeTable(sid)
                _state.update { it.copy(currentLapTimeTable = table) }
            } catch (e: Exception) {
                android.util.Log.e("PaddockViewModel", "Failed to fetch lap times", e)
            }
        }
    }
    
    fun fetchScorecard(sid: String) {
        viewModelScope.launch {
            try {
                val sc = api.scorecard(sid)
                _state.update { it.copy(currentScorecard = sc) }
            } catch (e: Exception) {
                android.util.Log.e("PaddockViewModel", "Failed to fetch scorecard", e)
            }
        }
    }
    
    fun fetchPedalBehavior(sid: String) {
        viewModelScope.launch {
            try {
                val pb = api.pedalBehavior(sid)
                _state.update { it.copy(currentPedalBehavior = pb) }
            } catch (e: Exception) {
                android.util.Log.e("PaddockViewModel", "Failed to fetch pedal behavior", e)
            }
        }
    }

    // ── Existing actions ──────────────────────────────────────────────────────
    fun toggleFocus(markerId: String) {
        _state.update { s ->
            val next = s.selectedMarkerIds.toMutableSet()
            if (next.contains(markerId)) next.remove(markerId)
            else if (next.size < 3) next.add(markerId)
            s.copy(selectedMarkerIds = next)
        }
    }

    fun clearFocus() {
        _state.update { it.copy(selectedMarkerIds = emptySet()) }
    }

    fun setUseMph(useMph: Boolean) = _state.update { it.copy(useMph = useMph) }

    val markers: List<TrackMarker> get() = MockSonomaData.markers

    // ── Insights — fetch per lap ──────────────────────────────────────────────
    fun fetchPendingInsights() {
        val completedLaps = _state.value.laps.map { it.lap }
        val toFetch = completedLaps.filter { it !in fetchedLaps }
        if (toFetch.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(insightsLoading = true, insightsError = false) }
            val newInsights = mutableListOf<DriverInsight>()
            var anyError = false

            for (lap in toFetch) {
                val json = bridge.getInsightsJson(lap)
                if (json == null) { anyError = true; continue }
                val parsed = parseInsights(json, lap)
                if (parsed.isNotEmpty()) {
                    newInsights.addAll(parsed)
                    fetchedLaps.add(lap)
                }
            }

            _state.update { state ->
                if (newInsights.isNotEmpty()) {
                    state.copy(
                        insights       = state.insights + newInsights,
                        insightsLoading = false,
                        insightsError  = anyError,
                    )
                } else {
                    state.copy(insightsLoading = false, insightsError = anyError)
                }
            }
        }
    }

    fun refreshInsights() {
        fetchedLaps.clear()
        _state.update { it.copy(insights = emptyList()) }
        fetchPendingInsights()
    }

    private fun parseInsights(json: String, lap: Int): List<DriverInsight> = try {
        val root = org.json.JSONObject(json)
        val arr  = root.getJSONArray("insights")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val cornersArr = o.optJSONArray("corners")
            val corners = if (cornersArr != null)
                (0 until cornersArr.length()).map { cornersArr.getString(it) }
            else emptyList()
            DriverInsight(
                id             = o.getString("id"),
                rank           = o.optInt("rank", i + 1),
                title          = o.getString("title"),
                detail         = o.getString("detail"),
                corners        = corners,
                metricLabel    = o.optString("metric_label", ""),
                metricValue    = o.optString("metric_value", ""),
                effort         = o.optInt("effort", 2),
                estGainS       = o.optDouble("est_gain_s", 0.0).toFloat(),
                evidenceBursts = o.optInt("evidence_bursts", 0),
                lap            = lap,
            )
        }
    } catch (e: Exception) {
        android.util.Log.w("PaddockViewModel", "Insight parse error: ${e.message}")
        emptyList()
    }

    // ── Track outline — load once from assets ─────────────────────────────────
    fun loadTrackOutline(context: Context) {
        if (_state.value.trackOutline != null) return
        viewModelScope.launch {
            val outline = withContext(Dispatchers.IO) {
                try {
                    context.assets.open("tracks/sonoma.json").bufferedReader().readText()
                        .let { TrackOutline.fromJson(it) }
                } catch (e: Exception) {
                    android.util.Log.w("PaddockViewModel", "Could not load track outline: ${e.message}")
                    null
                }
            }
            if (outline != null) _state.update { it.copy(trackOutline = outline) }
        }
    }
}
