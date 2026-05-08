package com.pitwall.paddock.service

import android.content.Context
import android.content.Intent
import android.util.Log

object TermuxLauncher {
    private const val TAG = "TermuxLauncher"

    fun bootServer(context: Context) {
        Log.i(TAG, "Sending RUN_COMMAND intent to Termux to boot server...")
        try {
            val intent = Intent("com.termux.RUN_COMMAND").apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra(
                    "com.termux.RUN_COMMAND_ARGUMENTS", 
                    arrayOf("-c", "termux-wake-lock && cd ~/pitwall && source .venv/bin/activate && python -m src.pitwall --can-interface slcan --can-channel socket://127.0.0.1:9000")
                )
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home/pitwall")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0") // 0 = default, don't keep session open if background
            }
            try {
                context.startService(intent)
            } catch (e: IllegalStateException) {
                context.startForegroundService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send boot intent to Termux. Is Termux installed?", e)
        }
    }

    fun shutdownServer(context: Context) {
        Log.i(TAG, "Sending RUN_COMMAND intent to Termux to shutdown server...")
        try {
            val intent = Intent("com.termux.RUN_COMMAND").apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra(
                    "com.termux.RUN_COMMAND_ARGUMENTS", 
                    arrayOf("-c", "pkill -f 'python -m src.pitwall' ; termux-wake-unlock")
                )
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            try {
                context.startService(intent)
            } catch (e: IllegalStateException) {
                context.startForegroundService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send shutdown intent to Termux.", e)
        }
    }
}
