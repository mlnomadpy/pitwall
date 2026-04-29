package com.pitwall.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.pitwall.app.MainActivity
import com.pitwall.app.data.CoachingMessage
import com.pitwall.app.data.TelemetryFrame
import com.pitwall.app.data.TrackMap
import com.pitwall.app.fusion.SensorFusion
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import android.net.Uri

private const val TAG = "PitwallService"
private const val NOTIF_CHANNEL = "pitwall_session"
private const val NOTIF_ID = 1001
private const val BURST_INTERVAL_MS = 5_000L
private const val RING_BUFFER_MAX = 50

/**
 * Foreground service — thin VBO/BT relay to the Python bridge.
 *
 * Exposes:
 *   [telemetry]  StateFlow<TelemetryFrame?> — consumed by PitwallViewModel (Compose)
 *   [coaching]   SharedFlow<CoachingMessage> — consumed by PitwallViewModel (Compose)
 *
 * No on-device ML, no audio, no EventChannel sinks — all coaching lives in pitwall_bridge.py.
 */
class PitwallService : LifecycleService() {

    inner class LocalBinder : Binder() {
        val service get() = this@PitwallService
    }

    private val binder = LocalBinder()

    private lateinit var track: TrackMap
    private lateinit var fusion: SensorFusion
    private val bridge = BridgeClient()

    // ── Public Compose-ready flows ────────────────────────────────────────────
    private val _telemetry = MutableStateFlow<TelemetryFrame?>(null)
    val telemetry: StateFlow<TelemetryFrame?> = _telemetry.asStateFlow()

    private val _coaching = MutableSharedFlow<CoachingMessage>(extraBufferCapacity = 32)
    val coaching: SharedFlow<CoachingMessage> = _coaching.asSharedFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // Session state
    private var driverLevel = "intermediate"
    private var sessionJob: Job? = null
    private var frameCount = 0
    private var lapCount = 0
    private var bestLapTime: Float? = null

    // Ring buffer for bursts
    private val ringBuffer = ArrayDeque<TelemetryFrame>(RING_BUFFER_MAX)
    private var lastBurstAt = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Note: startForeground() is called in onStartCommand(), not here.
        // Calling it here (when created via bindService) throws
        // ForegroundServiceStartNotAllowedException on API 34.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Promote to foreground immediately — must happen within 5s of startForegroundService()
        startForeground(NOTIF_ID, buildNotification("Session starting…"))
        val replayPath = intent?.getStringExtra(EXTRA_REPLAY_PATH)
        val level = intent?.getStringExtra(EXTRA_DRIVER_LEVEL) ?: "intermediate"
        driverLevel = level
        lifecycleScope.launch { initialise(replayPath) }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    private suspend fun initialise(replayPath: String?) {
        track = loadSonomaTrack()
        Log.i(TAG, "Track: ${track.name}, ${track.corners.size} corners")

        fusion = SensorFusion(track)

        sessionJob = fusion.frames
            .onEach { frame -> processFrame(frame) }
            .launchIn(lifecycleScope)

        if (replayPath != null) {
            val uri = Uri.parse(replayPath)
            ReplayService(this, uri, fusion).start(lifecycleScope)
        } else {
            Log.i(TAG, "Live mode: waiting for Bluetooth sensors")
        }

        _isRunning.value = true
        updateNotification("Session running — ${track.name}")
    }

    private suspend fun processFrame(frame: TelemetryFrame) {
        frameCount++

        // Push telemetry to Compose UI
        _telemetry.value = frame

        // Maintain ring buffer
        if (ringBuffer.size >= RING_BUFFER_MAX) ringBuffer.removeFirst()
        ringBuffer.addLast(frame)

        // Burst to Python bridge every BURST_INTERVAL_MS
        val now = System.currentTimeMillis()
        if (now - lastBurstAt >= BURST_INTERVAL_MS && ringBuffer.isNotEmpty()) {
            lastBurstAt = now
            val burstJson = serialiseBurst(ringBuffer.toList())
            lifecycleScope.launch {
                val coachingText = bridge.analyze(burstJson)
                if (coachingText != null) {
                    val msg = CoachingMessage(
                        text = coachingText,
                        priority = 1,
                        source = CoachingMessage.Source.WARM_PATH,
                        targetCorner = null,
                    )
                    _coaching.tryEmit(msg)
                    Log.i(TAG, "Bridge coaching: \"$coachingText\"")
                }
            }
        }
    }

    // ── Public API (called from ViewModel via binder) ─────────────────────────

    fun setDriverLevel(level: String) {
        driverLevel = level.lowercase()
    }

    fun stopSession() {
        sessionJob?.cancel()
        ringBuffer.clear()
        _isRunning.value = false
        _telemetry.value = null
        stopSelf()
    }

    fun getSessionStats(): Map<String, Any?> = mapOf(
        "frameCount" to frameCount,
        "lapCount" to lapCount,
        "bestLapTime" to bestLapTime,
        "bridgeUrl" to "http://127.0.0.1:8765",
    )

    // ── Burst serialisation ───────────────────────────────────────────────────

    private fun serialiseBurst(frames: List<TelemetryFrame>): String {
        val avgSpeedKmh = frames.map { it.speedKmh }.average()
        val maxComboG   = frames.maxOf { it.comboG.value }
        val maxLateralG = frames.maxOf { it.gLat.value }
        val maxLongG    = frames.maxOf { it.gLong.value }
        val maxBrake    = frames.maxOf { it.brake.value }
        val avgThrottle = frames.map { it.throttle.value }.average()
        val coastFrames = frames.count { it.isCoasting }
        val trailFrames = frames.count { it.brake.value > 3f && it.inCorner }
        val distanceM   = frames.lastOrNull()?.distance?.value ?: 0f
        val inCorner    = frames.lastOrNull()?.inCorner ?: false
        val pastApex    = frames.lastOrNull()?.pastApex ?: false
        val lap         = frames.lastOrNull()?.lap ?: 0
        val lapTime     = frames.lastOrNull()?.lapTime ?: 0f
        val corners     = frames.mapNotNull { it.currentCorner }.distinct()
            .joinToString(",") { "\"$it\"" }
        return buildString {
            append('{')
            append("\"driver_level\":\"$driverLevel\",")
            append("\"track\":\"sonoma\",\"car\":\"BMW M3\",")
            append("\"frame_count\":${frames.size},")
            append("\"lap\":$lap,\"lap_time_s\":$lapTime,")
            append("\"avg_speed_kmh\":${avgSpeedKmh.toFloat()},")
            append("\"max_combo_g\":$maxComboG,")
            append("\"max_lateral_g\":$maxLateralG,")
            append("\"max_long_g\":$maxLongG,")
            append("\"max_brake_bar\":$maxBrake,")
            append("\"avg_throttle_pct\":${avgThrottle.toFloat()},")
            append("\"coast_frames\":$coastFrames,")
            append("\"trail_brake_frames\":$trailFrames,")
            append("\"distance_m\":$distanceM,")
            append("\"in_corner\":$inCorner,")
            append("\"past_apex\":$pastApex,")
            append("\"corners_visited\":[$corners]")
            append('}')
        }
    }

    // ── Track loading ─────────────────────────────────────────────────────────

    private fun loadSonomaTrack(): TrackMap {
        return try {
            assets.open("tracks/sonoma.json").use { Json.decodeFromStream<TrackMap>(it) }
        } catch (e: Exception) {
            Log.w(TAG, "sonoma.json missing: ${e.message} — using stub")
            stubSonomaTrack()
        }
    }

    private fun stubSonomaTrack(): TrackMap = TrackMap(
        name = "Sonoma Raceway", trackLength = 3765f,
        sfLat = 38.1614, sfLon = -122.4549,
        corners = listOf(
            com.pitwall.app.data.TrackCorner("Turn 1",  150f,  220f,  300f, "L", 2, 0f,   111f, 113f, 117f, 0f),
            com.pitwall.app.data.TrackCorner("Turn 3",  600f,  700f,  820f, "R", 4, 50f,  104f, 87f,  102f, 11f),
            com.pitwall.app.data.TrackCorner("Turn 6",  1200f, 1320f, 1440f,"R", 5, 86f,  92f,  77f,  105f, -11f),
            com.pitwall.app.data.TrackCorner("Turn 9",  2100f, 2200f, 2300f,"L", 3, 66f,  121f, 116f, 132f, -16f),
            com.pitwall.app.data.TrackCorner("Turn 10", 2500f, 2620f, 2760f,"R", 6, 124f, 106f, 73f,  108f, 0f),
            com.pitwall.app.data.TrackCorner("Turn 11", 2900f, 3020f, 3200f,"R", 5, 134f, 88f,  64f,  95f,  0f),
        ),
        sectors = listOf(
            com.pitwall.app.data.TrackSector("Sector 1", 0f,    1255f),
            com.pitwall.app.data.TrackSector("Sector 2", 1255f, 2510f),
            com.pitwall.app.data.TrackSector("Sector 3", 2510f, 3765f),
        ),
    )

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL, "Pitwall Session", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "AI racing coach session" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun updateNotification(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("Pitwall — AI Racing Coach")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val EXTRA_REPLAY_PATH  = "replay_path"
        const val EXTRA_DRIVER_LEVEL = "driver_level"
    }
}
