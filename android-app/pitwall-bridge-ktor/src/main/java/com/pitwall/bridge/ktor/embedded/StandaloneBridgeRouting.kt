package com.pitwall.bridge.ktor.embedded

import android.content.Context
import com.pitwall.bridge.ktor.AnalyzeResponsePayload
import com.pitwall.bridge.ktor.CoachingEngine
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Full standalone HTTP surface for the Vue PWA: sessions, DuckDB export, coaching stubs,
 * SSE cues, and analysis bundle proxies — no Python process required.
 */
internal data class StandaloneBridgeRuntime(
    val context: Context,
    val duck: EmbeddedDuckDb,
    val sessions: EmbeddedSessionRepository,
    val cues: EmbeddedCueBroadcaster,
    val coachingEngine: CoachingEngine,
    val ktorJson: Json,
    val bridgeScope: CoroutineScope,
    val analyzedHook: suspend (AnalyzeResponsePayload) -> Unit,
    val activeSessionId: AtomicReference<String?>,
    val sessionBundles: ConcurrentHashMap<String, JsonObject>,
    val burstHistory: MutableList<JsonObject>,
)

internal fun Application.configureStandaloneBridgeRoutes(
    runtime: StandaloneBridgeRuntime,
    staticRoot: File?,
) {
    val syncBursts: (JsonObject) -> Unit = { o ->
        synchronized(runtime.burstHistory) {
            runtime.burstHistory.add(o)
            while (runtime.burstHistory.size > 2500) {
                runtime.burstHistory.removeAt(0)
            }
        }
    }

    routing {
        get("/health") {
            val base = runtime.coachingEngine.healthPayload()
            context.respond(
                base.copy(
                    activeSessionId = runtime.activeSessionId.get(),
                    duckdb = true,
                ),
            )
        }

        post("/analyze") {
            val raw = context.receiveText()
            val burstEl = runCatching { runtime.ktorJson.parseToJsonElement(raw) }.getOrNull()
            val burstObj = burstEl?.jsonObject
            if (burstObj != null) syncBursts(burstObj)
            val payload = runtime.coachingEngine.analyze(raw)
            runtime.bridgeScope.launch {
                try {
                    runtime.analyzedHook(payload)
                } catch (_: Throwable) {
                }
            }
            val sid = burstObj?.get("session_id")?.jsonPrimitive?.contentOrNull
            if (sid != null && isSafeSessionId(sid)) {
                val burstId = burstObj["burst_id"]?.jsonPrimitive?.intOrNull ?: payload.burstId
                val dist = burstObj["distance_m"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                runtime.duck.withConnection {
                    runtime.duck.insertCoachingNote(
                        this,
                        sid,
                        burstId,
                        dist,
                        payload.coaching,
                        payload.source,
                    )
                }
                val cueObj = kotlinx.serialization.json.buildJsonObject {
                    put("id", JsonPrimitive("burst-$burstId"))
                    put("text", JsonPrimitive(payload.coaching))
                    put("emotion", JsonPrimitive("neutral"))
                    put("timestamp", JsonPrimitive(System.currentTimeMillis()))
                }
                val cue = cueObj.toString()
                runtime.cues.publish(sid, cue)
            }
            context.respond(payload)
        }

        get("/sessions") {
            val limit = context.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val activeOnly = context.request.queryParameters["active_only"]?.equals("true", true) == true
            context.respond(runtime.sessions.listSessions(limit, activeOnly))
        }

        post("/session/start") {
            val body = runCatching {
                runtime.ktorJson.parseToJsonElement(context.receiveText()).jsonObject
            }.getOrNull() ?: JsonObject(emptyMap())
            val track = body["track"]?.jsonPrimitive?.contentOrNull
            val sid = body["session_id"]?.jsonPrimitive?.contentOrNull?.takeIf { isSafeSessionId(it) }
                ?: newSessionId(track)
            val driver = body["driver"]?.jsonPrimitive?.contentOrNull
            val level = body["driver_level"]?.jsonPrimitive?.contentOrNull
            val car = body["car"]?.jsonPrimitive?.contentOrNull
            val note = body["note"]?.jsonPrimitive?.contentOrNull
            runtime.sessions.ensureSession(sid, driver, level, track, car, note)
            runtime.activeSessionId.set(sid)
            context.respond(
                kotlinx.serialization.json.buildJsonObject {
                    put("started", JsonPrimitive(true))
                    put("session_id", JsonPrimitive(sid))
                },
            )
        }

        post("/session/{sid}/end") {
            val sid = context.parameters["sid"] ?: return@post context.respond(HttpStatusCode.BadRequest)
            if (!isSafeSessionId(sid)) return@post context.respond(HttpStatusCode.BadRequest)
            runtime.sessions.endSession(sid)
            if (runtime.activeSessionId.get() == sid) runtime.activeSessionId.set(null)
            context.respond(
                kotlinx.serialization.json.buildJsonObject {
                    put("ended", JsonPrimitive(true))
                    put("session_id", JsonPrimitive(sid))
                },
            )
        }

        get("/session/{sid}") {
            val sid = context.parameters["sid"] ?: return@get context.respond(HttpStatusCode.BadRequest)
            if (!isSafeSessionId(sid)) return@get context.respond(HttpStatusCode.BadRequest)
            val detail = runtime.sessions.sessionDetail(sid)
            if (detail == null) {
                context.respond(
                    HttpStatusCode.NotFound,
                    kotlinx.serialization.json.buildJsonObject {
                        put("error", JsonPrimitive("session not found"))
                        put("session_id", JsonPrimitive(sid))
                    },
                )
            } else {
                context.respond(detail)
            }
        }

        post("/session/{sid}/frames") {
            val sid = context.parameters["sid"] ?: return@post context.respond(HttpStatusCode.BadRequest)
            if (!isSafeSessionId(sid)) return@post context.respond(HttpStatusCode.BadRequest)
            val raw = context.receiveText()
            val root = runCatching { runtime.ktorJson.parseToJsonElement(raw).jsonObject }.getOrNull()
                ?: return@post context.respond(HttpStatusCode.BadRequest)
            val framesJson = root["frames"]?.jsonArray ?: return@post context.respond(
                HttpStatusCode.BadRequest,
                kotlinx.serialization.json.buildJsonObject {
                    put("saved", JsonPrimitive(false))
                    put("error", JsonPrimitive("no frames"))
                },
            )
            val rows = framesJson.mapNotNull { el ->
                val f = el.jsonObject
                EmbeddedDuckDb.FrameRow(
                    timestamp = f["timestamp"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    distanceM = (f["distance"] ?: f["distance_m"])?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    speedMs = f["speed"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    gLat = f["g_lat"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    gLong = f["g_long"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    comboG = f["combo_g"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    brakeBar = f["brake_pressure"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    throttlePct = f["throttle"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    steeringDeg = f["steering"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    rpm = f["rpm"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    lat = f["lat"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    lon = f["lon"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                )
            }
            if (rows.isEmpty()) {
                return@post context.respond(
                    HttpStatusCode.BadRequest,
                    kotlinx.serialization.json.buildJsonObject {
                        put("saved", JsonPrimitive(false))
                        put("error", JsonPrimitive("no frames"))
                    },
                )
            }
            runtime.sessions.ensureSession(
                sid,
                root["driver"]?.jsonPrimitive?.contentOrNull,
                root["driver_level"]?.jsonPrimitive?.contentOrNull,
                root["track"]?.jsonPrimitive?.contentOrNull,
                root["car"]?.jsonPrimitive?.contentOrNull,
                root["note"]?.jsonPrimitive?.contentOrNull,
            )
            val n = runtime.duck.withConnection { runtime.duck.appendFrames(this, sid, rows) }
            context.respond(
                kotlinx.serialization.json.buildJsonObject {
                    put("saved", JsonPrimitive(true))
                    put("session_id", JsonPrimitive(sid))
                    put("n_appended", JsonPrimitive(n))
                },
            )
        }

        get("/session/{sid}/export.parquet") {
            val sid = context.parameters["sid"] ?: return@get context.respond(HttpStatusCode.BadRequest)
            if (!isSafeSessionId(sid)) return@get context.respond(HttpStatusCode.BadRequest)
            val table = context.request.queryParameters["table"]?.lowercase() ?: "telemetry"
            if (table != "telemetry") {
                return@get context.respond(
                    HttpStatusCode.BadRequest,
                    kotlinx.serialization.json.buildJsonObject {
                        put("error", JsonPrimitive("embedded bridge exports telemetry only"))
                        put("table", JsonPrimitive(table))
                    },
                )
            }
            val count = runtime.duck.withConnection { runtime.duck.countTelemetry(this, sid) }
            if (count == 0L) {
                return@get context.respond(
                    HttpStatusCode.NotFound,
                    kotlinx.serialization.json.buildJsonObject {
                        put("error", JsonPrimitive("session not found in this table"))
                        put("session_id", JsonPrimitive(sid))
                        put("table", JsonPrimitive(table))
                    },
                )
            }
            val tmp = File.createTempFile("pw-", ".parquet", runtime.context.cacheDir)
            try {
                runtime.duck.withConnection { runtime.duck.exportTelemetryParquet(this, sid, tmp) }
                val bytes = tmp.readBytes()
                context.respondBytes(bytes, ContentType.Application.OctetStream)
            } finally {
                tmp.delete()
            }
        }

        get("/insights") {
            val lap = context.request.queryParameters["lap"]?.toIntOrNull()
            val bursts = synchronized(runtime.burstHistory) { runtime.burstHistory.toList() }
            context.respond(EmbeddedInsights.scoreInsights(bursts, lap))
        }

        get("/coach/concepts") {
            context.respondText(EMBEDDED_COACH_CONCEPTS_JSON, ContentType.Application.Json)
        }

        get("/coach/brief") {
            context.respondText(EMBEDDED_COACH_BRIEF_JSON, ContentType.Application.Json)
        }

        post("/coach/debrief") {
            val body = runCatching { runtime.ktorJson.parseToJsonElement(context.receiveText()).jsonObject }.getOrNull()
                ?: JsonObject(emptyMap())
            val sid = body["session_id"]?.jsonPrimitive?.contentOrNull ?: return@post context.respond(
                HttpStatusCode.BadRequest,
                kotlinx.serialization.json.buildJsonObject { put("error", JsonPrimitive("session_id required")) },
            )
            if (!isSafeSessionId(sid)) return@post context.respond(HttpStatusCode.BadRequest)
            val n = runtime.duck.withConnection { runtime.duck.countTelemetry(this, sid) }
            if (n == 0L) {
                return@post context.respond(
                    HttpStatusCode.BadRequest,
                    kotlinx.serialization.json.buildJsonObject {
                        put("error", JsonPrimitive("no telemetry — push frames first"))
                        put("session_id", JsonPrimitive(sid))
                    },
                )
            }
            val laps = runtime.duck.withConnection { runtime.duck.listLaps(this, sid) }
            val best = laps.mapNotNull { it.lapTimeS }.minOrNull()
            val bundle = kotlinx.serialization.json.buildJsonObject {
                put("session_id", JsonPrimitive(sid))
                put(
                    "scorecard",
                    kotlinx.serialization.json.buildJsonObject {
                        put("n_laps", JsonPrimitive(laps.size))
                        put("best_lap_s", if (best != null) JsonPrimitive(best) else JsonNull)
                        put("corners", JsonArray(emptyList()))
                    },
                )
                put("highlights", JsonArray(emptyList()))
                put("narrative", JsonPrimitive("Embedded session summary — telemetry frames: $n."))
                put("narrative_source", JsonPrimitive("embedded_ktor"))
                put("emotion", JsonPrimitive("neutral"))
                put("focus", JsonArray(listOf(JsonPrimitive("Smooth entries"), JsonPrimitive("Commit on exit"))))
            }
            runtime.sessionBundles[sid] = bundle
            context.respond(bundle)
        }

        post("/coach/ask") {
            context.respondText(
                """
                {"answer":"Coach Q&A runs fully when ADK is available. On device, focus on consistent laps and brake release points.","emotion":"neutral","qa_key":"embedded","turn":1}
                """.trimIndent(),
                ContentType.Application.Json,
            )
        }

        post("/coach/ask/end") {
            context.respondText("""{"flushed":0,"qa_key":"embedded"}""", ContentType.Application.Json)
        }

        post("/lap") {
            val body = runCatching { runtime.ktorJson.parseToJsonElement(context.receiveText()).jsonObject }.getOrNull()
                ?: JsonObject(emptyMap())
            if (!runtime.sessions.saveLap(body)) {
                return@post context.respond(HttpStatusCode.BadRequest)
            }
            context.respondText("""{"saved":true}""", ContentType.Application.Json)
        }

        get("/laps") {
            val qSession = context.request.queryParameters["session_id"]?.takeIf { isSafeSessionId(it) }
            val limit = context.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val laps = runtime.duck.withConnection {
                runtime.duck.queryLapsListing(this, qSession, limit)
            }
            context.respond(
                buildJsonObject {
                    put("laps", laps)
                    put("count", JsonPrimitive(laps.size))
                },
            )
        }

        get("/signals/registry") {
            context.respondText("""{"signals":[],"count":0}""", ContentType.Application.Json)
        }

        get("/diagnostics/llm_friction") {
            context.respondText("""{"events":[]}""", ContentType.Application.Json)
        }

        get("/cues/stream") {
            val sid = context.request.queryParameters["session_id"]
                ?: return@get context.respond(HttpStatusCode.BadRequest)
            val q = runtime.cues.subscribe(sid)
            try {
                context.respondOutputStream(ContentType.parse("text/event-stream")) {
                    write(":connected\n\n".toByteArray(Charsets.UTF_8))
                    while (true) {
                        val msg = EmbeddedCueBroadcaster.drainWithTimeout(q, 15000L)
                        if (msg != null) {
                            write(EmbeddedCueBroadcaster.formatEvent(msg).toByteArray(Charsets.UTF_8))
                        } else {
                            write(EmbeddedCueBroadcaster.heartbeatComment().toByteArray(Charsets.UTF_8))
                        }
                        flush()
                    }
                }
            } finally {
                runtime.cues.unsubscribe(sid, q)
            }
        }

        route("/session/{sid}") {
            fun bundleSection(sid: String, section: String): Pair<HttpStatusCode, JsonObject> {
                if (!isSafeSessionId(sid)) {
                    return Pair(
                        HttpStatusCode.BadRequest,
                        kotlinx.serialization.json.buildJsonObject { put("error", JsonPrimitive("bad sid")) },
                    )
                }
                val bundle = runtime.sessionBundles[sid] ?: return Pair(
                    HttpStatusCode.NotFound,
                    kotlinx.serialization.json.buildJsonObject {
                        put("error", JsonPrimitive("session not analysed; POST /coach/debrief first"))
                        put("session_id", JsonPrimitive(sid))
                    },
                )
                val sectionEl = bundle[section] ?: JsonNull
                return Pair(
                    HttpStatusCode.OK,
                    kotlinx.serialization.json.buildJsonObject {
                        put("session_id", JsonPrimitive(sid))
                        put(section, sectionEl)
                    },
                )
            }

            get("/scorecard") {
                val sid = context.parameters["sid"] ?: return@get context.respond(HttpStatusCode.BadRequest)
                val (code, json) = bundleSection(sid, "scorecard")
                context.respond(code, json)
            }
            get("/highlights") {
                val sid = context.parameters["sid"] ?: return@get context.respond(HttpStatusCode.BadRequest)
                val (code, json) = bundleSection(sid, "highlights")
                context.respond(code, json)
            }
            get("/stats") {
                val sid = context.parameters["sid"] ?: return@get context.respond(HttpStatusCode.BadRequest)
                val (code, json) = bundleSection(sid, "stats")
                context.respond(code, json)
            }
            get("/friction_circle") {
                val sid = context.parameters["sid"] ?: return@get context.respond(HttpStatusCode.BadRequest)
                val (code, json) = bundleSection(sid, "friction_circle")
                context.respond(code, json)
            }
            get("/hustle_map") {
                val sid = context.parameters["sid"] ?: return@get context.respond(HttpStatusCode.BadRequest)
                val (code, json) = bundleSection(sid, "hustle_map")
                context.respond(code, json)
            }
            get("/eob") {
                val sid = context.parameters["sid"] ?: return@get context.respond(HttpStatusCode.BadRequest)
                val (code, json) = bundleSection(sid, "eob")
                context.respond(code, json)
            }
            get("/incidents") {
                val sid = context.parameters["sid"] ?: return@get context.respond(HttpStatusCode.BadRequest)
                val (code, json) = bundleSection(sid, "incidents")
                context.respond(code, json)
            }
            get("/map") {
                val sid = context.parameters["sid"] ?: return@get context.respond(HttpStatusCode.BadRequest)
                val (code, json) = bundleSection(sid, "map")
                context.respond(code, json)
            }
            get("/clips") {
                val sid = context.parameters["sid"] ?: return@get context.respond(HttpStatusCode.BadRequest)
                val (code, json) = bundleSection(sid, "clips")
                context.respond(code, json)
            }

            get("/pedal_behavior") {
                val sid = context.parameters["sid"] ?: return@get context.respond(HttpStatusCode.BadRequest)
                if (!isSafeSessionId(sid)) return@get context.respond(HttpStatusCode.BadRequest)
                val thrTh = context.request.queryParameters["throttle_th"]?.toDoubleOrNull() ?: 5.0
                val brkTh = context.request.queryParameters["brake_th"]?.toDoubleOrNull() ?: 1.0
                val rows = runtime.duck.withConnection { runtime.duck.pedalBehaviorRows(this, sid) }
                if (rows.isEmpty()) {
                    return@get context.respond(
                        HttpStatusCode.NotFound,
                        kotlinx.serialization.json.buildJsonObject {
                            put("error", JsonPrimitive("session not found"))
                            put("session_id", JsonPrimitive(sid))
                        },
                    )
                }
                val states = mutableMapOf(
                    "throttle_only" to 0,
                    "brake_only" to 0,
                    "trail_brake" to 0,
                    "coast" to 0,
                )
                for ((_, thr, brk) in rows) {
                    val onThr = thr > thrTh
                    val onBrk = brk > brkTh
                    when {
                        onThr && onBrk -> states["trail_brake"] = states.getValue("trail_brake") + 1
                        onThr -> states["throttle_only"] = states.getValue("throttle_only") + 1
                        onBrk -> states["brake_only"] = states.getValue("brake_only") + 1
                        else -> states["coast"] = states.getValue("coast") + 1
                    }
                }
                val n = rows.size
                val deltas = if (rows.size > 1) {
                    rows.zipWithNext().map { (a, b) -> kotlin.math.abs(b.first - a.first) }.sorted()
                } else {
                    listOf(0.1)
                }
                val frameDt = deltas.getOrElse(deltas.size / 2) { 0.1 }
                val statesOut = kotlinx.serialization.json.buildJsonObject {
                    for ((k, v) in states) {
                        put(
                            k,
                            kotlinx.serialization.json.buildJsonObject {
                                put("frames", JsonPrimitive(v))
                                put("pct", JsonPrimitive(if (n > 0) 100.0 * v / n else 0.0))
                                put("time_s", JsonPrimitive(v * frameDt))
                            },
                        )
                    }
                }
                context.respond(
                    kotlinx.serialization.json.buildJsonObject {
                        put("session_id", JsonPrimitive(sid))
                        put("frame_count", JsonPrimitive(n))
                        put(
                            "thresholds",
                            kotlinx.serialization.json.buildJsonObject {
                                put("throttle_pct", JsonPrimitive(thrTh))
                                put("brake_bar", JsonPrimitive(brkTh))
                            },
                        )
                        put("frame_dt_s", JsonPrimitive(frameDt))
                        put("states", statesOut)
                    },
                )
            }

            get("/lap_time_table") {
                val sid = context.parameters["sid"] ?: return@get context.respond(HttpStatusCode.BadRequest)
                if (!isSafeSessionId(sid)) return@get context.respond(HttpStatusCode.BadRequest)
                val samples = runtime.duck.withConnection { runtime.duck.loadTelemetryOrdered(this, sid) }
                runtime.duck.persistDetectedLapsIfEmpty(sid, samples)
                val table = EmbeddedLapPresentation.lapTimeTable(sid, samples)
                if (table == null) {
                    context.respond(
                        HttpStatusCode.BadRequest,
                        buildJsonObject {
                            put("error", JsonPrimitive("no complete laps detected"))
                            put("session_id", JsonPrimitive(sid))
                        },
                    )
                } else {
                    context.respond(table)
                }
            }

            get("/lap_time_distribution") {
                val sid = context.parameters["sid"] ?: return@get context.respond(HttpStatusCode.BadRequest)
                if (!isSafeSessionId(sid)) return@get context.respond(HttpStatusCode.BadRequest)
                val samples = runtime.duck.withConnection { runtime.duck.loadTelemetryOrdered(this, sid) }
                runtime.duck.persistDetectedLapsIfEmpty(sid, samples)
                val rows = runtime.duck.withConnection { runtime.duck.listLaps(this, sid) }
                val dist = EmbeddedLapStats.lapTimeDistribution(sid, rows)
                if (dist.containsKey("error")) {
                    context.respond(HttpStatusCode.BadRequest, dist)
                } else {
                    context.respond(dist)
                }
            }
            get("/ideal_lap") {
                val sid = context.parameters["sid"] ?: return@get context.respond(HttpStatusCode.BadRequest)
                if (!isSafeSessionId(sid)) return@get context.respond(HttpStatusCode.BadRequest)
                val samples = runtime.duck.withConnection { runtime.duck.loadTelemetryOrdered(this, sid) }
                runtime.duck.persistDetectedLapsIfEmpty(sid, samples)
                val payload = EmbeddedLapPresentation.idealLap(sid, samples)
                if (payload.containsKey("error")) {
                    context.respond(HttpStatusCode.BadRequest, payload)
                } else {
                    context.respond(payload)
                }
            }
            get("/sector_times") {
                val sid = context.parameters["sid"] ?: return@get context.respond(HttpStatusCode.BadRequest)
                if (!isSafeSessionId(sid)) return@get context.respond(HttpStatusCode.BadRequest)
                val samples = runtime.duck.withConnection { runtime.duck.loadTelemetryOrdered(this, sid) }
                runtime.duck.persistDetectedLapsIfEmpty(sid, samples)
                val out = EmbeddedLapPresentation.sectorTimes(sid, samples)
                if (out == null) {
                    context.respond(
                        HttpStatusCode.BadRequest,
                        buildJsonObject {
                            put("error", JsonPrimitive("no complete laps detected"))
                            put("session_id", JsonPrimitive(sid))
                        },
                    )
                } else {
                    context.respond(out)
                }
            }
            get("/throttle_corner_box") {
                val sid = context.parameters["sid"] ?: ""
                context.respondText(
                    """{"session_id":"$sid","corners":[],"error":"no corner geometry on embedded"}""",
                    ContentType.Application.Json,
                )
            }
            get("/corner_classification") {
                val sid = context.parameters["sid"] ?: ""
                context.respondText("""{"session_id":"$sid","corners":[]}""", ContentType.Application.Json)
            }
            get("/straight_line_speed") {
                context.respondText("""{"samples":[]}""", ContentType.Application.Json)
            }
            get("/brake_acceleration") {
                context.respondText("""{"samples":[]}""", ContentType.Application.Json)
            }
        }

        get("/track/markers") {
            context.respondText("""{"markers":[],"count":0}""", ContentType.Application.Json)
        }
        get("/track/danger_zones") {
            context.respondText("""{"zones":[]}""", ContentType.Application.Json)
        }
        get("/track/weather") {
            context.respondText(
                """{"phase":"clear","surface":"dry","note":"Embedded default"}""",
                ContentType.Application.Json,
            )
        }
        get("/markers") {
            context.respondText("""{"markers":[],"count":0}""", ContentType.Application.Json)
        }

        val root = staticRoot?.takeIf { it.exists() }?.canonicalFile
        if (root != null) {
            get("{path...}") {
                val segments = context.parameters.getAll("path") ?: emptyList()
                val relative = segments.joinToString("/").trim('/')
                val indexFile = File(root, "index.html")
                val target = if (relative.isEmpty()) {
                    indexFile
                } else {
                    File(root, relative).canonicalFile
                }
                if (!target.path.startsWith(root.path)) {
                    context.respond(HttpStatusCode.Forbidden)
                    return@get
                }
                when {
                    target.isFile -> context.respondFile(target)
                    File(target, "index.html").isFile -> context.respondFile(File(target, "index.html"))
                    indexFile.isFile -> context.respondFile(indexFile)
                    else -> context.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}

private const val EMBEDDED_COACH_CONCEPTS_JSON =
    """{"source":"Ross Bentley — Performance Driving Illustrated","concepts":[{"id":"trail_brake","description":"Smoothly release brake as steering increases at corner entry.","fires_when":"in_corner"},{"id":"exit_speed","description":"Throttle on early — exit speed beats corner speed.","fires_when":"past_apex"}],"count":2}"""

private const val EMBEDDED_COACH_BRIEF_JSON =
    """{"driver_id":"","date":"","weather_phase":"clear","surface_state":"dry","weather_note":"Embedded standalone brief.","weakest_recent_corner":null,"biggest_recent_improvement":null,"danger_zones_today":[],"narrative_md":"**Embedded Pitwall** — offline coaching stack. Pick three focus corners and run clean laps.","focus":["Brake release","Eyes up","Rhythm"],"emotion":"neutral"}"""
