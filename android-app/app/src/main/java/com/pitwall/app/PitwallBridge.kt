package com.pitwall.app

import android.util.Log
import com.chaquo.python.Python
import java.io.File

/**
 * Starts the embedded Pitwall Flask bridge on a background thread (port 8765).
 * Requires [Python.start] before first use.
 */
object PitwallBridge {
    private const val TAG = "PitwallBridge"

    @Volatile
    private var started = false

    fun start(application: android.app.Application) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            val home = File(application.filesDir, "pitwall_home").absolutePath
            PitwallAndroidHome.prepare(application, home)
            Thread(
                {
                    try {
                        Python.getInstance()
                            .getModule("pitwall_android_launcher")
                            .callAttr("main", home)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Embedded bridge exited or crashed", t)
                    }
                },
                "pitwall-bridge",
            ).start()
        }
    }
}
