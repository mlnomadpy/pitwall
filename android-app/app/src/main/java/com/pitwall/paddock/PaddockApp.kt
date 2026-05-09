package com.pitwall.paddock

import android.app.Application
import android.util.Log
import com.pitwall.bridge.ktor.DefaultCoachingEngine
import com.pitwall.bridge.ktor.PitwallEmbeddedBridge
import com.pitwall.paddock.data.NetworkModule
import com.pitwall.paddock.data.analytics.DuckDbSpike
import com.pitwall.paddock.data.local.AnalyzeEventEntity
import com.pitwall.paddock.data.local.PitwallDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "PaddockApp"

class PaddockApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        val db = PitwallDatabase.get(this)

        if (BuildConfig.PITWALL_USE_EMBEDDED_BRIDGE) {
            val rawPath = BuildConfig.PITWALL_LLM_MODEL_PATH.trim()
            val llmPath = rawPath.takeUnless { it.isEmpty() }
            val engine = DefaultCoachingEngine(this, llmPath)
            PitwallEmbeddedBridge.onAnalyzed = { payload ->
                db.analyzeEventDao().insert(
                    AnalyzeEventEntity(
                        coaching = payload.coaching,
                        burstId = payload.burstId,
                        source = payload.source,
                        createdAtEpochMs = System.currentTimeMillis(),
                    ),
                )
            }
            PitwallEmbeddedBridge.start(applicationContext, engine)
        }

        appScope.launch {
            val duckFile = File(filesDir, "pitwall_analytics.duckdb")
            DuckDbSpike.verifyConnection(duckFile)?.let {
                Log.i(TAG, "DuckDB JDBC spike: $it (${duckFile.absolutePath})")
            }
        }

        NetworkModule.init(BuildConfig.PITWALL_API_BASE_URL_EFFECTIVE)
    }
}
