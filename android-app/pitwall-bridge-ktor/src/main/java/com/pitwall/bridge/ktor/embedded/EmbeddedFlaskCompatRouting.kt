package com.pitwall.bridge.ktor.embedded

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Registers Flask-shaped routes not covered elsewhere so the Vue PWA rarely hits 404 on `/8765`.
 * Heavy analytics remain stubs where noted; capabilities/signals mirror Flask where DuckDB supports them.
 */
@Suppress("LongMethod")
internal fun Routing.embeddedFlaskCompatRoutes(runtime: StandaloneBridgeRuntime) {

    get("/conversations/{sid}") {
        val sid = context.parameters["sid"] ?: return@get badSid(context)
        if (!isSafeSessionId(sid)) return@get badSid(context)
        context.respond(
            buildJsonObject {
                put("session_id", JsonPrimitive(sid))
                put("conversations", JsonArray(emptyList()))
            },
        )
    }

    get("/conversations/driver/{driver_id}") {
        val did = context.parameters["driver_id"] ?: ""
        context.respond(
            buildJsonObject {
                put("driver_id", JsonPrimitive(did))
                put("conversations", JsonArray(emptyList()))
            },
        )
    }

    post("/score") {
        context.respondText("""{"saved":false,"note":"embedded_stub"}""", ContentType.Application.Json)
    }

    post("/coach/ask/stream") {
        context.respondText(
            """{"error":"embedded_stub","detail":"use POST /coach/ask for offline coach"}""",
            ContentType.Application.Json,
            HttpStatusCode.NotImplemented,
        )
    }

    get("/coach/agents") {
        context.respondText(
            """{"available":false,"agents":[],"source":"embedded"}""",
            ContentType.Application.Json,
        )
    }

    get("/track/{track_id}/elevation") {
        val tid = context.parameters["track_id"] ?: ""
        context.respondText(
            """{"track_id":"${tid.escapeJson()}","samples":[],"source":"embedded_stub"}""",
            ContentType.Application.Json,
        )
    }

    get("/driver/{driver_id}/evolution") {
        val did = context.parameters["driver_id"] ?: ""
        context.respondText(
            """{"driver_id":"${did.escapeJson()}","sessions":[],"source":"embedded_stub"}""",
            ContentType.Application.Json,
        )
    }

    get("/driver/{driver_id}/profile") {
        val did = context.parameters["driver_id"] ?: ""
        context.respondText(
            """{"driver_id":"${did.escapeJson()}","profile":null,"source":"embedded_stub"}""",
            ContentType.Application.Json,
        )
    }

    get("/session/{sid}/corners") {
        val sid = context.parameters["sid"] ?: return@get badSid(context)
        if (!isSafeSessionId(sid)) return@get badSid(context)
        context.respondText(
            """{"session_id":"${sid.escapeJson()}","corners":[],"source":"embedded_stub"}""",
            ContentType.Application.Json,
        )
    }

    get("/session/{sid}/capabilities") {
        val sid = context.parameters["sid"] ?: return@get badSid(context)
        if (!isSafeSessionId(sid)) return@get badSid(context)
        val json =
            runtime.duck.withConnection {
                runtime.duck.querySessionCapabilitiesJson(this, sid)
            }
        if (json == null) {
            context.respond(
                HttpStatusCode.NotFound,
                buildJsonObject {
                    put("error", JsonPrimitive("session not found"))
                    put("session_id", JsonPrimitive(sid))
                },
            )
        } else {
            context.respond(json)
        }
    }

    get("/session/{sid}/signals") {
        val sid = context.parameters["sid"] ?: return@get badSid(context)
        if (!isSafeSessionId(sid)) return@get badSid(context)
        val namesParam = context.request.queryParameters["names"] ?: ""
        val names = namesParam.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (names.isEmpty()) {
            context.respond(
                HttpStatusCode.BadRequest,
                buildJsonObject { put("error", JsonPrimitive("names is required (comma-separated)")) },
            )
            return@get
        }
        val axis = (context.request.queryParameters["axis"] ?: "gps").trim()
        val interp = (context.request.queryParameters["interp"] ?: "hold").trim().lowercase()
        if (interp !in setOf("hold", "lerp")) {
            context.respond(
                HttpStatusCode.BadRequest,
                buildJsonObject { put("error", JsonPrimitive("interp must be 'hold' or 'lerp'")) },
            )
            return@get
        }
        val rateHz = context.request.queryParameters["rate_hz"]?.toDoubleOrNull() ?: 0.0
        if (rateHz < 0) {
            context.respond(
                HttpStatusCode.BadRequest,
                buildJsonObject { put("error", JsonPrimitive("rate_hz must be >= 0")) },
            )
            return@get
        }
        val tFrom = context.request.queryParameters["t_from"]?.toDoubleOrNull()
        val tTo = context.request.queryParameters["t_to"]?.toDoubleOrNull()
        if (context.request.queryParameters["lap"] != null) {
            context.respond(
                HttpStatusCode.BadRequest,
                buildJsonObject { put("error", JsonPrimitive("lap clipping not implemented on embedded")) },
            )
            return@get
        }

        val payload =
            runtime.duck.withConnection {
                runtime.duck.querySessionSignalsJson(this, sid, names, axis, interp, rateHz, tFrom, tTo)
            }
        when {
            payload == null ->
                context.respond(
                    HttpStatusCode.NotFound,
                    buildJsonObject {
                        put("error", JsonPrimitive("session not found"))
                        put("session_id", JsonPrimitive(sid))
                    },
                )
            payload["error"] != null ->
                context.respond(HttpStatusCode.BadRequest, payload)
            else -> context.respond(payload)
        }
    }

    post("/session/{sid}/signals") {
        val sid = context.parameters["sid"] ?: return@post badSid(context)
        if (!isSafeSessionId(sid)) return@post badSid(context)
        val body =
            runCatching { runtime.ktorJson.parseToJsonElement(context.receiveText()).jsonObject }.getOrNull()
                ?: JsonObject(emptyMap())
        val arr = body["signals"]?.jsonArray
        if (arr == null || arr.isEmpty()) {
            context.respond(
                HttpStatusCode.BadRequest,
                buildJsonObject { put("error", JsonPrimitive("no signals")) },
            )
            return@post
        }
        val triples = ArrayList<Triple<String, Double, Double>>()
        val namesSeen = mutableSetOf<String>()
        val discovered = mutableListOf<String>()
        for (el in arr) {
            val o = el.jsonObject
            val nm = o["name"]?.jsonPrimitive?.contentOrNull ?: continue
            val t = o["t"]?.jsonPrimitive?.doubleOrNull ?: continue
            val v = o["value"]?.jsonPrimitive?.doubleOrNull ?: continue
            triples.add(Triple(nm, t, v))
            namesSeen.add(nm)
        }
        if (triples.isEmpty()) {
            context.respond(
                HttpStatusCode.BadRequest,
                buildJsonObject {
                    put("error", JsonPrimitive("no valid samples (need name, t, value)"))
                },
            )
            return@post
        }
        val (written, caps) =
            runtime.duck.withConnection {
                val n = runtime.duck.appendTelemetrySignals(this, sid, triples)
                val c = runtime.duck.computeCapabilities(this, sid)
                n to c
            }
        context.respond(
            buildJsonObject {
                put("saved", JsonPrimitive(true))
                put("session_id", JsonPrimitive(sid))
                put("n_appended", JsonPrimitive(written))
                put("names", JsonArray(namesSeen.sorted().map { JsonPrimitive(it) }))
                put("newly_discovered", JsonArray(discovered.map { JsonPrimitive(it) }))
                put("capabilities_count", JsonPrimitive(caps))
            },
        )
    }

    get("/session/{sid}/sync") {
        val sid = context.parameters["sid"] ?: return@get badSid(context)
        if (!isSafeSessionId(sid)) return@get badSid(context)
        context.respondText(
            """{"session_id":"${sid.escapeJson()}","telemetry":[],"video":[],"source":"embedded_stub"}""",
            ContentType.Application.Json,
        )
    }

    post("/session/{sid}/video_frames") {
        val sid = context.parameters["sid"] ?: return@post badSid(context)
        if (!isSafeSessionId(sid)) return@post badSid(context)
        context.respondText(
            """{"saved":false,"session_id":"${sid.escapeJson()}","note":"video_frames not stored on embedded"}""",
            ContentType.Application.Json,
        )
    }

    post("/session/import") {
        context.respondText(
            """{"saved":false,"error":"session import not implemented on embedded"}""",
            ContentType.Application.Json,
            HttpStatusCode.NotImplemented,
        )
    }

    post("/session/reset") {
        context.respondText("""{"reset":true,"source":"embedded"}""", ContentType.Application.Json)
    }

    post("/session/{sid}/frame") {
        val sid = context.parameters["sid"] ?: return@post badSid(context)
        if (!isSafeSessionId(sid)) return@post badSid(context)
        val raw = context.receiveText()
        val f =
            runCatching { runtime.ktorJson.parseToJsonElement(raw).jsonObject }.getOrNull()
                ?: return@post context.respond(HttpStatusCode.BadRequest)
        val row =
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
        val n =
            runtime.duck.withConnection {
                runtime.duck.appendFrames(this, sid, listOf(row))
            }
        context.respond(
            buildJsonObject {
                put("saved", JsonPrimitive(true))
                put("session_id", JsonPrimitive(sid))
                put("n_appended", JsonPrimitive(n))
            },
        )
    }

    get("/telemetry/stream") {
        context.respondText(
            ":telemetry_stream embedded stub — use CAN ingest + DuckDB\n\n",
            ContentType.parse("text/event-stream"),
        )
    }

    get("/notifications") {
        context.respondText("""{"notifications":[],"source":"embedded"}""", ContentType.Application.Json)
    }

    post("/spectator/token") {
        context.respondText(
            """{"token":"","expires_at":0,"source":"embedded_stub"}""",
            ContentType.Application.Json,
        )
    }

    delete("/spectator/token/{token}") {
        context.respondText("""{"deleted":false,"source":"embedded_stub"}""", ContentType.Application.Json)
    }
}

private suspend fun badSid(call: ApplicationCall) {
    call.respond(HttpStatusCode.BadRequest, buildJsonObject { put("error", JsonPrimitive("bad sid")) })
}

private fun String.escapeJson(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")
