package com.pitwall.app

import android.app.Application
import com.pitwall.app.bridge.PitwallKotlinBridge

/**
 * Starts the on-device Kotlin/Ktor HTTP bridge ([docs/api.md] compatible).
 * Python/Chaquopy is no longer required for the shipping APK path.
 */
class PitwallApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PitwallKotlinBridge.start(this)
    }
}
