package com.pitwall.app.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pitwall.app.data.CoachingMessage
import com.pitwall.app.data.CornerSessionStats
import com.pitwall.app.data.DriverInsight
import com.pitwall.app.data.TelemetryFrame
import com.pitwall.app.data.TrackOutline
import com.pitwall.app.service.PitwallService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppMode { SETUP, ON_TRACK, PADDOCK }

/**
 * Represents the connection status of a single hardware peripheral.
 *
 * @param displayName   Human-readable label shown in SetupScreen.
 * @param connected     True if the device is bonded/reachable (or we are on an emulator).
 * @param isEmulator    True when running on an AVD — status is synthetic, not real BT state.
 */
data class HardwareStatus(
    val displayName: String,
    val connected: Boolean,
    val isEmulator: Boolean = false,
)

data class PitwallUiState(
    val mode: AppMode = AppMode.SETUP,
    val telemetry: TelemetryFrame? = null,
    val lastCoaching: CoachingMessage? = null,
    val driverLevel: String = "intermediate",
    val isStarting: Boolean = false,
    val slowSinceMs: Long? = null,
    /** Empty until [PitwallViewModel.refreshHardwareStatus] is called. */
    val hardwareStatus: List<HardwareStatus> = emptyList(),
    // ── Analysis data ─────────────────────────────────────────────────────
    val laps: List<com.pitwall.app.service.LapRecord> = emptyList(),
    val insights: List<DriverInsight> = emptyList(),
    val insightsLoading: Boolean = false,
    val insightsError: Boolean = false,
    val trackOutline: TrackOutline? = null,
    val cornerStats: Map<String, CornerSessionStats> = emptyMap(),
)

/**
 * Sealed event that MainActivity observes to perform Activity-context operations
 * (starting foreground service, binding, unbinding).
 * ViewModel never holds an Activity or service reference directly.
 */
sealed class SessionCommand {
    data class Start(val replayPath: String?, val level: String) : SessionCommand()
    object Stop : SessionCommand()
}

class PitwallViewModel(application: Application) : AndroidViewModel(application) {

    private val _ui = MutableStateFlow(PitwallUiState())
    val ui: StateFlow<PitwallUiState> = _ui.asStateFlow()

    /** MainActivity collects this to execute Activity-context service calls. */
    private val _commands = MutableSharedFlow<SessionCommand>(extraBufferCapacity = 8)
    val commands: SharedFlow<SessionCommand> = _commands.asSharedFlow()

    private var service: PitwallService? = null
    private var telemetryJob: Job? = null
    private var coachingJob: Job? = null
    private var lapsJob: Job? = null
    private var insightsPollingJob: Job? = null
    private val bridgeClient = com.pitwall.app.service.BridgeClient()

    // ── Called by MainActivity after service bind ─────────────────────────────

    fun onServiceConnected(svc: PitwallService) {
        service = svc
        telemetryJob?.cancel()
        coachingJob?.cancel()
        lapsJob?.cancel()
        telemetryJob = viewModelScope.launch {
            svc.telemetry.collect { frame ->
                if (frame == null) return@collect
                _ui.update { it.copy(telemetry = frame) }
                checkAutoTransition(frame)
            }
        }
        coachingJob = viewModelScope.launch {
            svc.coaching.collect { msg ->
                _ui.update { it.copy(lastCoaching = msg) }
            }
        }
        lapsJob = viewModelScope.launch {
            svc.laps.collect { list ->
                _ui.update { it.copy(laps = list) }
                if (_ui.value.mode == AppMode.PADDOCK) {
                    fetchPendingInsights()
                }
            }
        }
    }

    fun onServiceDisconnected() {
        service = null
        telemetryJob?.cancel()
        coachingJob?.cancel()
        lapsJob?.cancel()
    }

    // ── UI actions ────────────────────────────────────────────────────────────

    fun startSession(replayPath: String?, level: String) {
        _ui.update { it.copy(isStarting = true, driverLevel = level) }
        viewModelScope.launch {
            _commands.emit(SessionCommand.Start(replayPath, level))
        }
    }

    fun onSessionStarted() {
        _ui.update { it.copy(isStarting = false, mode = AppMode.ON_TRACK) }
    }

    fun stopSession() {
        viewModelScope.launch { _commands.emit(SessionCommand.Stop) }
        telemetryJob?.cancel()
        coachingJob?.cancel()
        _ui.update { PitwallUiState() }
    }

    fun enterPaddock() {
        _ui.update { it.copy(mode = AppMode.PADDOCK) }
        fetchPendingInsights()
        refreshCornerStats()
    }

    fun returnToTrack() {
        _ui.update { it.copy(mode = AppMode.ON_TRACK) }
    }

    fun setDriverLevel(level: String) = _ui.update { it.copy(driverLevel = level) }

    /**
     * Probes real Bluetooth bonded-device state and pushes [HardwareStatus] entries
     * into [PitwallUiState.hardwareStatus].
     *
     * On an Android emulator (detected via [Build.FINGERPRINT]) all devices are
     * reported as connected so the emulator-only workflow is unaffected.
     *
     * On a real device each expected peripheral is matched against bonded devices by
     * a case-insensitive prefix of its display name.  Missing devices do NOT block
     * session start — the SetupScreen shows a warning banner instead.
     *
     * Requires BLUETOOTH_CONNECT permission (already declared in AndroidManifest).
     */
    @SuppressLint("MissingPermission")
    fun refreshHardwareStatus(context: Context) {
        val onEmulator = isEmulator()

        val expected = listOf(
            "Racelogic Mini" to "racelogic",
            "OBDLink MX"     to "obdlink",
            "Pixel Earbuds"  to "pixel buds",
        )

        val statuses: List<HardwareStatus> = if (onEmulator) {
            // Emulator: no real BT adapter — pass-through all as connected
            expected.map { (name, _) ->
                HardwareStatus(displayName = name, connected = true, isEmulator = true)
            }
        } else {
            val bondedNames: Set<String> = try {
                val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
                btManager?.adapter?.bondedDevices
                    ?.mapTo(mutableSetOf()) { it.name.orEmpty().lowercase() }
                    ?: emptySet()
            } catch (e: SecurityException) {
                // BLUETOOTH_CONNECT not yet granted — treat all as unknown/missing
                emptySet()
            }

            expected.map { (name, prefix) ->
                val found = bondedNames.any { it.contains(prefix) }
                HardwareStatus(displayName = name, connected = found, isEmulator = false)
            }
        }

        _ui.update { it.copy(hardwareStatus = statuses) }
    }

    /** Returns true when running inside an Android emulator (AVD / Genymotion). */
    private fun isEmulator(): Boolean {
        val fp = Build.FINGERPRINT.lowercase()
        return fp.contains("generic") ||
            fp.contains("emulator") ||
            fp.contains("sdk_gphone") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.HARDWARE == "goldfish" ||
            Build.HARDWARE == "ranchu"
    }

    // ── Insights — fetch per lap ────────────────────────────────────────────

    private val fetchedLaps = mutableSetOf<Int>()

    fun fetchPendingInsights() {
        val completedLaps = _ui.value.laps.map { it.lap }
        val toFetch = completedLaps.filter { it !in fetchedLaps }
        if (toFetch.isEmpty()) return

        viewModelScope.launch {
            _ui.update { it.copy(insightsLoading = true, insightsError = false) }
            val newInsights = mutableListOf<DriverInsight>()
            var anyError = false

            for (lap in toFetch) {
                val json = bridgeClient.getInsightsJson(lap)
                if (json == null) {
                    anyError = true
                    continue
                }
                val parsed = parseInsights(json, lap)
                if (parsed.isNotEmpty()) {
                    newInsights.addAll(parsed)
                    fetchedLaps.add(lap)
                }
            }

            if (newInsights.isNotEmpty()) {
                _ui.update { state -> 
                    state.copy(
                        insights = state.insights + newInsights,
                        insightsLoading = false,
                        insightsError = anyError
                    )
                }
            } else {
                _ui.update { it.copy(insightsLoading = false, insightsError = anyError) }
            }
        }
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
        android.util.Log.w("PitwallViewModel", "Insight parse error: ${e.message}")
        emptyList()
    }

    // ── Track outline — load once from assets ─────────────────────────────

    fun loadTrackOutline(context: Context) {
        if (_ui.value.trackOutline != null) return  // already loaded
        viewModelScope.launch {
            val outline = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    context.assets.open("tracks/sonoma.json").bufferedReader().readText()
                        .let { com.pitwall.app.data.TrackOutline.fromJson(it) }
                } catch (e: Exception) {
                    android.util.Log.w("PitwallViewModel", "Could not load track outline: ${e.message}")
                    null
                }
            }
            if (outline != null) _ui.update { it.copy(trackOutline = outline) }
        }
    }

    // ── Corner stats — pull from service ─────────────────────────────────

    fun refreshCornerStats() {
        val svc = service ?: return
        val track = try { svc.getTrackMap() } catch (_: Exception) { null } ?: return
        val stats = svc.getCornerStats(track)
        _ui.update { it.copy(cornerStats = stats) }
    }

    // ── Auto-transition ───────────────────────────────────────────────────────

    private fun checkAutoTransition(frame: TelemetryFrame) {
        val now = System.currentTimeMillis()
        val mode = _ui.value.mode
        when {
            frame.speedMph < 5f && mode == AppMode.ON_TRACK -> {
                val since = _ui.value.slowSinceMs ?: now
                _ui.update { it.copy(slowSinceMs = since) }
                if (now - since >= 30_000L) enterPaddock()
            }
            else -> _ui.update { it.copy(slowSinceMs = null) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        telemetryJob?.cancel()
        coachingJob?.cancel()
    }
}
