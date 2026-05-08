package com.pitwall.app

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

/**
 * Starts Chaquopy once per process and launches the embedded Flask bridge.
 */
class PitwallApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        PitwallBridge.start(this)
    }
}
