package com.pitwall.bridge.ktor

import android.content.Context
import android.util.Log
import com.pitwall.bridge.ktor.embedded.EmbeddedBridgeRegistry
import com.pitwall.bridge.ktor.embedded.EmbeddedCueBroadcaster
import com.pitwall.bridge.ktor.embedded.EmbeddedDuckDb
import com.pitwall.bridge.ktor.embedded.EmbeddedSessionRepository
import com.pitwall.bridge.ktor.embedded.StandaloneBridgeRuntime
import com.pitwall.bridge.ktor.embedded.configureStandaloneBridgeRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "PitwallEmbeddedBridge"

/**
 * Embedded HTTP server on **127.0.0.1:8765**: Vue `vite` build (same origin), DuckDB-backed sessions,
 * Flask-compatible coaching/session/analytics routes — **no Python bridge required**.
 */
object PitwallEmbeddedBridge {

    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: ApplicationEngine? = null

    var onAnalyzed: suspend (AnalyzeResponsePayload) -> Unit = {}

    fun start(
        context: Context,
        coachingEngine: CoachingEngine,
    ) {
        if (engine != null) return
        val staticRoot = try {
            PwaAssetExtractor.ensureInstalled(context)
        } catch (e: Exception) {
            Log.e(TAG, "vue dist missing — run npm run build in src/pwa and sync assets/pwa-www", e)
            null
        }

        val ktorJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

        val dbFile = File(context.filesDir, "pitwall_embedded.sqlite")
        val duck = EmbeddedDuckDb(dbFile)
        runBlocking(Dispatchers.IO) {
            duck.withConnection {
                try {
                    val json =
                        context.assets.open("registry/obd2_pids.json").bufferedReader().use { it.readText() }
                    duck.seedSignalRegistry(this, json)
                } catch (e: Exception) {
                    Log.w(TAG, "signal_registry seed skipped (assets/registry/obd2_pids.json)", e)
                }
            }
        }
        val sessions = EmbeddedSessionRepository(duck)
        val cues = EmbeddedCueBroadcaster()
        val activeSessionId = AtomicReference<String?>(null)
        val sessionBundles = ConcurrentHashMap<String, JsonObject>()
        val burstHistory = Collections.synchronizedList(mutableListOf<JsonObject>())

        val runtime = StandaloneBridgeRuntime(
            context = context.applicationContext,
            duck = duck,
            sessions = sessions,
            cues = cues,
            coachingEngine = coachingEngine,
            ktorJson = ktorJson,
            bridgeScope = bridgeScope,
            analyzedHook = { onAnalyzed(it) },
            activeSessionId = activeSessionId,
            sessionBundles = sessionBundles,
            burstHistory = burstHistory,
        )
        EmbeddedBridgeRegistry.attach(runtime)

        val server = embeddedServer(CIO, host = "127.0.0.1", port = 8765) {
            install(ContentNegotiation) {
                json(ktorJson)
            }
            configureStandaloneBridgeRoutes(runtime, staticRoot)
        }
        engine = server
        server.start(wait = false)
        Log.i(TAG, "Standalone embedded bridge at http://127.0.0.1:8765 (static=${staticRoot != null}, duckdb=${dbFile.absolutePath})")
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 200, timeoutMillis = 1000)
        engine = null
        EmbeddedBridgeRegistry.detach()
    }
}
