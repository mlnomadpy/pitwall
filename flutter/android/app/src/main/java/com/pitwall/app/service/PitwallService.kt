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
import com.pitwall.app.arbiter.MessageArbiter
import com.pitwall.app.audio.AudioEngine
import com.pitwall.app.data.SonomaGoldStandard
import com.pitwall.app.data.TelemetryFrame
import com.pitwall.app.data.TrackMap
import com.pitwall.app.fusion.SensorFusion
import com.pitwall.app.hotpath.DriverLevel
import com.pitwall.app.hotpath.GemmaEngine
import com.pitwall.app.hotpath.PedagogicalVectors
import com.pitwall.app.hotpath.SonicModel
import com.pitwall.app.pipeline.AntigravityPipeline
import io.flutter.plugin.common.EventChannel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File

private const val TAG = "PitwallService"
private const val NOTIF_CHANNEL = "pitwall_session"
private const val NOTIF_ID = 1001

/**
 * Main foreground service — the orchestrator.
 *
 * Manages lifecycle of:
 *   SensorFusion → HotPath (Gemma + SonicModel) → MessageArbiter → AudioEngine
 *                        ↕
 *              AntigravityPipeline (burst → Vertex AI → warm path coaching)
 *
 * Also bridges the live data to Flutter via EventChannel sinks registered
 * by MainActivity.
 */
class PitwallService : LifecycleService() {

    inner class LocalBinder : Binder() {
        val service get() = this@PitwallService
    }

    private val binder = LocalBinder()

    // Kotlin engine components
    private lateinit var track: TrackMap
    private lateinit var fusion: SensorFusion
    private lateinit var gemma: GemmaEngine
    private lateinit var arbiter: MessageArbiter
    private lateinit var audio: AudioEngine
    private lateinit var antigravity: AntigravityPipeline

    // Flutter event sinks (nullable — set when Flutter is listening)
    @Volatile private var telemetrySink: EventChannel.EventSink? = null
    @Volatile private var coachingSink: EventChannel.EventSink? = null

    // Session state
    private var driverLevel = DriverLevel.INTERMEDIATE
    private var sessionJob: Job? = null
    private var bestLapTime: Float? = null
    private var lapCount = 0
    private var frameCount = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Initialising…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val replayPath = intent?.getStringExtra(EXTRA_REPLAY_PATH)
        lifecycleScope.launch {
            initialise(replayPath)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    private suspend fun initialise(replayPath: String?) {
        // Load track
        track = loadSonomaTrack()
        Log.i(TAG, "Track loaded: ${track.name}, ${track.corners.size} corners")

        // Sensor fusion
        fusion = SensorFusion(track)

        // Gemma engine (best-effort)
        gemma = GemmaEngine(this)
        val gemmaAvailable = gemma.initialize()
        Log.i(TAG, "Gemma available: $gemmaAvailable")

        // Arbiter + audio
        arbiter = MessageArbiter()
        audio = AudioEngine(this)
        audio.initializeTts { Log.i(TAG, "TTS ready") }

        // Antigravity pipeline
        val sessionId = "sonoma-${System.currentTimeMillis()}"
        antigravity = AntigravityPipeline(this, sessionId) { coaching ->
            arbiter.submit(coaching)
        }

        // Wire: Racelogic + OBD → fusion → hot path + Antigravity → arbiter → audio + Flutter
        sessionJob = fusion.frames
            .onEach { frame -> processFrame(frame) }
            .launchIn(lifecycleScope)

        // Start hardware (or replay)
        if (replayPath != null) {
            ReplayService(this, File(replayPath), fusion).start(lifecycleScope)
        } else {
            // RacelogicService and OBDService start here (stubbed — pair in UI first)
            Log.i(TAG, "Live mode: pair Racelogic Mini and OBDLink MX via Bluetooth")
        }

        updateNotification("Session running — ${track.name}")
    }

    private suspend fun processFrame(frame: TelemetryFrame) {
        frameCount++

        // 1. Feed Antigravity ring buffer
        antigravity.addFrame(frame)
        antigravity.tick()   // sends burst if interval elapsed

        // 2. Hot path — Gemma or rule-based fallback
        val coaching = if (gemma.available) {
            val vectors = PedagogicalVectors.matching(frame)
            gemma.evaluate(frame, vectors, driverLevel, emptyList())
        } else null

        if (coaching != null) {
            arbiter.submit(coaching)
        }

        // 3. Sonic model cues (tone layer, always active)
        val cues = SonicModel.computeCues(frame)
        lifecycleScope.launch { audio.playTones(cues) }

        // 4. Lap estimate at sector boundaries
        if (frame.sector != null && frame.lap > 0) {
            val pct = (frame.distance.value % track.trackLength) / track.trackLength
            if (pct in 0.32..0.34 || pct in 0.65..0.67) {
                SonicModel.computeLapEstimateCue(
                    frame.distance.value, frame.lapTime, track.trackLength, bestLapTime
                )?.let { cues + listOf(it) }
            }
        }

        // 5. Arbiter evaluation → audio delivery
        val delivered = arbiter.evaluate(frame)
        if (delivered != null) {
            audio.speak(delivered.text, delivered.priority)
            // Push coaching event to Flutter
            coachingSink?.let { sink ->
                runOnMainThread { sink.success(delivered.toChannelMap()) }
            }
        }

        // 6. Push telemetry to Flutter HUD
        if (frameCount % 1 == 0) {   // every frame (10Hz)
            telemetrySink?.let { sink ->
                runOnMainThread { sink.success(frame.toChannelMap()) }
            }
        }
    }

    // ── Public API (called from MainActivity via binder) ───────────────────────

    fun setTelemetrySink(sink: EventChannel.EventSink?) { telemetrySink = sink }
    fun setCoachingSink(sink: EventChannel.EventSink?) { coachingSink = sink }

    fun setDriverLevel(level: String) {
        driverLevel = when (level.lowercase()) {
            "beginner" -> DriverLevel.BEGINNER
            "pro" -> DriverLevel.PRO
            else -> DriverLevel.INTERMEDIATE
        }
    }

    fun stopSession() {
        sessionJob?.cancel()
        gemma.close()
        audio.release()
        stopSelf()
    }

    fun getSessionStats(): Map<String, Any?> = mapOf(
        "frameCount" to frameCount,
        "lapCount" to lapCount,
        "bestLapTime" to bestLapTime,
        "gemmaAvailable" to gemma.available,
    )

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun runOnMainThread(block: () -> Unit) {
        mainThread.post(block)
    }

    private val mainThread = android.os.Handler(android.os.Looper.getMainLooper())

    private fun loadSonomaTrack(): TrackMap {
        // Load sonoma.json from assets (copied from src/simulator/sonoma.json)
        return try {
            assets.open("tracks/sonoma.json").use { stream ->
                Json.decodeFromStream<TrackMap>(stream)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load sonoma.json: ${e.message} — using stub track")
            stubSonomaTrack()
        }
    }

    private fun stubSonomaTrack(): TrackMap {
        // Minimal stub track from architecture.md data — used when JSON is missing
        return TrackMap(
            name = "Sonoma Raceway",
            trackLength = 3765f,
            sfLat = 38.1614,
            sfLon = -122.4549,
            corners = listOf(
                com.pitwall.app.data.TrackCorner("Turn 1", 150f, 220f, 300f, "L", 2, 0f, 111f, 113f, 117f, 0f),
                com.pitwall.app.data.TrackCorner("Turn 3", 600f, 700f, 820f, "R", 4, 50f, 104f, 87f, 102f, 11f),
                com.pitwall.app.data.TrackCorner("Turn 6", 1200f, 1320f, 1440f, "R", 5, 86f, 92f, 77f, 105f, -11f),
                com.pitwall.app.data.TrackCorner("Turn 9", 2100f, 2200f, 2300f, "L", 3, 66f, 121f, 116f, 132f, -16f),
                com.pitwall.app.data.TrackCorner("Turn 10", 2500f, 2620f, 2760f, "R", 6, 124f, 106f, 73f, 108f, 0f),
                com.pitwall.app.data.TrackCorner("Turn 11", 2900f, 3020f, 3200f, "R", 5, 134f, 88f, 64f, 95f, 0f),
            ),
            sectors = listOf(
                com.pitwall.app.data.TrackSector("Sector 1", 0f, 1255f),
                com.pitwall.app.data.TrackSector("Sector 2", 1255f, 2510f),
                com.pitwall.app.data.TrackSector("Sector 3", 2510f, 3765f),
            ),
        )
    }

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
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("Pitwall — AI Racing Coach")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val EXTRA_REPLAY_PATH = "replay_path"
    }
}
