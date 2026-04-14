package com.pitwall.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.IBinder
import com.pitwall.app.service.PitwallService
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

/**
 * MainActivity — FlutterActivity host.
 *
 * Sets up two Platform Channels between Flutter UI and the native Kotlin engine:
 *
 *   METHOD CHANNEL  "com.pitwall.app/control"
 *     → startSession(replayPath?)   start the foreground service
 *     → stopSession()               stop the service
 *     → setDriverLevel(level)       "beginner" | "intermediate" | "pro"
 *     → isOnline()                  check 5G/Network connectivity
 *
 *   EVENT CHANNEL   "com.pitwall.app/telemetry"
 *     ← TelemetryFrame map (every 10Hz frame)
 *
 *   EVENT CHANNEL   "com.pitwall.app/coaching"
 *     ← CoachingMessage map (on delivery from arbiter)
 */
class MainActivity : FlutterActivity() {

    private var pitwallService: PitwallService? = null
    private var serviceConnection: ServiceConnection? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // ── Method Channel: session control ───────────────────────────────────
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL_CONTROL
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "startSession" -> {
                    val replayPath = call.argument<String?>("replayPath")
                    startPitwallService(replayPath)
                    result.success(null)
                }
                "stopSession" -> {
                    pitwallService?.stopSession()
                    result.success(null)
                }
                "setDriverLevel" -> {
                    val level = call.argument<String>("level") ?: "intermediate"
                    pitwallService?.setDriverLevel(level)
                    result.success(null)
                }
                "getSessionStats" -> {
                    result.success(pitwallService?.getSessionStats())
                }
                "isOnline" -> {
                    result.success(isOnline())
                }
                else -> result.notImplemented()
            }
        }

        // ── Event Channel: telemetry frames ───────────────────────────────────
        EventChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL_TELEMETRY
        ).setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                pitwallService?.setTelemetrySink(events)
            }
            override fun onCancel(arguments: Any?) {
                pitwallService?.setTelemetrySink(null)
            }
        })

        // ── Event Channel: coaching messages ──────────────────────────────────
        EventChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL_COACHING
        ).setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                pitwallService?.setCoachingSink(events)
            }
            override fun onCancel(arguments: Any?) {
                pitwallService?.setCoachingSink(null)
            }
        })
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun startPitwallService(replayPath: String?) {
        val intent = Intent(this, PitwallService::class.java).apply {
            replayPath?.let { putExtra(PitwallService.EXTRA_REPLAY_PATH, it) }
        }
        startForegroundService(intent)

        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                pitwallService = (binder as PitwallService.LocalBinder).service
                // Register any pending event sinks
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                pitwallService = null
            }
        }
        bindService(intent, conn, BIND_AUTO_CREATE)
        serviceConnection = conn
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceConnection?.let { unbindService(it) }
    }

    companion object {
        const val CHANNEL_CONTROL = "com.pitwall.app/control"
        const val CHANNEL_TELEMETRY = "com.pitwall.app/telemetry"
        const val CHANNEL_COACHING = "com.pitwall.app/coaching"
    }
}
