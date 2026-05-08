package com.pitwall.app.bridge

import android.app.Application
import android.util.Log
import com.pitwall.app.bridge.inference.GemmaMediaPipeInference
import com.pitwall.app.bridge.inference.NativeCoachInference
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer

/**
 * Embedded HTTP bridge on `127.0.0.1:8765` — on-device Gemma via MediaPipe LLM Inference when a
 * `.task` bundle is present; otherwise coach endpoints fall back to rule-like stubs inside
 * [GemmaMediaPipeInference].
 */
object PitwallKotlinBridge {
    private const val TAG = "PitwallKotlinBridge"
    private val sessions = SessionRepository()

    @Volatile
    private lateinit var inference: NativeCoachInference

    @Volatile
    private var engine: io.ktor.server.engine.ApplicationEngine? = null

    fun start(app: Application) {
        if (engine != null) return
        synchronized(this) {
            if (engine != null) return
            inference = GemmaMediaPipeInference(app.applicationContext)
            try {
                val srv =
                    embeddedServer(CIO, host = "127.0.0.1", port = 8765) {
                        configureKotlinBridge(sessions, inference)
                    }
                srv.start(wait = false)
                engine = srv
                Log.i(TAG, "Kotlin bridge listening on http://127.0.0.1:8765")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start Kotlin bridge", t)
                throw t
            }
        }
    }
}
