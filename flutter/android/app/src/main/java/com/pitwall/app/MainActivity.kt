package com.pitwall.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.pitwall.app.service.PitwallService
import com.pitwall.app.ui.PitwallApp
import com.pitwall.app.ui.PitwallViewModel
import com.pitwall.app.ui.SessionCommand
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

/**
 * MainActivity — thin Compose host + service lifecycle owner.
 *
 * The ViewModel emits [SessionCommand] events; this Activity executes them
 * using Activity context (required for foreground service on API 31+).
 * The ViewModel never touches Context or Service directly.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: PitwallViewModel by viewModels()

    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "PitwallService connected")
            val svc = (binder as PitwallService.LocalBinder).service
            isBound = true
            viewModel.onServiceConnected(svc)
            viewModel.onSessionStarted()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(TAG, "PitwallService disconnected")
            isBound = false
            viewModel.onServiceDisconnected()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Observe ViewModel commands and execute them with Activity context
        lifecycleScope.launch {
            viewModel.commands.collect { cmd ->
                when (cmd) {
                    is SessionCommand.Start -> startAndBindService(cmd.replayPath, cmd.level)
                    is SessionCommand.Stop  -> stopAndUnbindService()
                }
            }
        }

        setContent {
            PitwallApp(viewModel)
        }
    }

    private fun startAndBindService(replayPath: String?, level: String) {
        // Take a persistent read permission for content:// URIs (file picker).
        // Without this the ContentResolver in PitwallService (a different context) can't read the file.
        if (replayPath != null && replayPath.startsWith("content://")) {
            try {
                contentResolver.takePersistableUriPermission(
                    android.net.Uri.parse(replayPath),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not take persistable URI permission: ${e.message}")
            }
        }

        val intent = Intent(this, PitwallService::class.java).apply {
            replayPath?.let { putExtra(PitwallService.EXTRA_REPLAY_PATH, it) }
            putExtra(PitwallService.EXTRA_DRIVER_LEVEL, level)
        }
        // Must use Activity context for startForegroundService on API 31+
        startForegroundService(intent)
        if (!isBound) {
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
        Log.i(TAG, "Started foreground service (replay=$replayPath, level=$level)")
    }

    private fun stopAndUnbindService() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        stopService(Intent(this, PitwallService::class.java))
        Log.i(TAG, "Stopped service")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            try { unbindService(serviceConnection) } catch (_: Exception) {}
            isBound = false
        }
    }
}
