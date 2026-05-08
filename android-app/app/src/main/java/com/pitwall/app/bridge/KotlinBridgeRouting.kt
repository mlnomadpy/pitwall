package com.pitwall.app.bridge

import com.pitwall.app.bridge.inference.NativeCoachInference
import com.pitwall.app.data.remote.AnalyzeRequestDto
import com.pitwall.app.data.remote.AnalyzeResponseDto
import com.pitwall.app.data.remote.BrakeAccelerationDto
import com.pitwall.app.data.remote.BriefResponseDto
import com.pitwall.app.data.remote.CoachAskEndRequestDto
import com.pitwall.app.data.remote.CoachAskEndResponseDto
import com.pitwall.app.data.remote.CoachAskRequestDto
import com.pitwall.app.data.remote.CoachAskResponseDto
import com.pitwall.app.data.remote.CoachConceptDto
import com.pitwall.app.data.remote.CoachConceptsResponse
import com.pitwall.app.data.remote.EndSessionResponse
import com.pitwall.app.data.remote.HealthResponse
import com.pitwall.app.data.remote.IdealLapDto
import com.pitwall.app.data.remote.InsightsResponseDto
import com.pitwall.app.data.remote.LapTimeDistributionDto
import com.pitwall.app.data.remote.LapTimeTableDto
import com.pitwall.app.data.remote.LlmScoreRequestDto
import com.pitwall.app.data.remote.LlmScoreResponseDto
import com.pitwall.app.data.remote.PedalBehaviorDto
import com.pitwall.app.data.remote.PedalThresholdsDto
import com.pitwall.app.data.remote.ScorecardEnvelopeDto
import com.pitwall.app.data.remote.SectorTimesResponseDto
import com.pitwall.app.data.remote.SessionClipsEnvelopeDto
import com.pitwall.app.data.remote.SessionDetailDto
import com.pitwall.app.data.remote.SessionsEnvelope
import com.pitwall.app.data.remote.SessionSummaryDto
import com.pitwall.app.data.remote.StartSessionRequest
import com.pitwall.app.data.remote.StartSessionResponse
import com.pitwall.app.data.remote.StraightLineSpeedDto
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.handle
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant

fun Application.configureKotlinBridge(
    sessions: SessionRepository,
    inference: NativeCoachInference,
) {
    install(ContentNegotiation) {
        json(bridgeJson)
    }

    routing {
        bridgeRoutes(sessions, inference)
    }
}

@Suppress("LongMethod")
private fun Route.bridgeRoutes(
    sessions: SessionRepository,
    inference: NativeCoachInference,
) {
    get("/health") {
        call.respond(
            HealthResponse(
                status = "ok",
                version = "kotlin-bridge-0.1",
                engine = "kotlin_bridge",
                coach = "native_stub",
                driverLevel = "intermediate",
                track = "Sonoma Raceway",
                duckdb = false,
                timestamp = Instant.now().toString(),
            ),
        )
    }

    get("/sessions") {
        val list = sessions.all()
        call.respond(SessionsEnvelope(sessions = list, count = list.size))
    }

    post("/session/start") {
        val body = bridgeJson.decodeFromString(StartSessionRequest.serializer(), call.receiveText())
        val sid = SessionRepository.newSessionId()
        val summary =
            SessionSummaryDto(
                sessionId = sid,
                driver = body.driver ?: "driver",
                driverLevel = body.driverLevel ?: "intermediate",
                track = body.track ?: "Sonoma Raceway",
                car = body.car ?: "car",
                startedAt = Instant.now().toString(),
                endedAt = null,
                note = body.note ?: "",
                lapCount = 0,
                bestLapS = null,
            )
        sessions.upsert(summary)
        call.respond(StartSessionResponse(started = true, sessionId = sid))
    }

    post("/session/reset") {
        sessions.all().forEach { sessions.remove(it.sessionId) }
        call.respond(JsonObject(emptyMap()))
    }

    route("/session/{id}") {
        get {
            val id = call.parameters["id"]
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            val s = sessions[id]
            if (s == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respond(
                SessionDetailDto(
                    session = s,
                    laps = emptyList(),
                    notes = emptyList(),
                    lapCount = s.lapCount,
                    bestLapS = s.bestLapS,
                ),
            )
        }
        post("/end") {
            val id = call.parameters["id"]
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            sessions.remove(id)
            call.respond(EndSessionResponse(ended = true, sessionId = id))
        }
        get("/lap_time_table") {
            val id = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            call.respond(
                LapTimeTableDto(
                    sessionId = id,
                    lapCount = 0,
                    bestLapS = 0.0,
                    bestLapNumber = 0,
                    laps = emptyList(),
                ),
            )
        }
        get("/lap_time_distribution") {
            val id = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            call.respond(
                LapTimeDistributionDto(
                    sessionId = id,
                    lapCount = 0,
                    minS = 0.0,
                    maxS = 0.0,
                    q1S = 0.0,
                    medianS = 0.0,
                    q3S = 0.0,
                    iqrS = 0.0,
                    whiskerLowS = 0.0,
                    whiskerHighS = 0.0,
                    outliers = emptyList(),
                    meanS = 0.0,
                    stddevS = 0.0,
                ),
            )
        }
        get("/ideal_lap") {
            val id = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            call.respond(
                IdealLapDto(
                    sessionId = id,
                    idealLapS = 0.0,
                    bestActualLapS = 0.0,
                    gainPotentialS = 0.0,
                    bestSectors = emptyList(),
                ),
            )
        }
        get("/sector_times") {
            val id = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            call.respond(
                SectorTimesResponseDto(
                    sessionId = id,
                    sectorDefinitions = emptyList(),
                    laps = emptyList(),
                ),
            )
        }
        get("/scorecard") {
            val id = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            call.respond(ScorecardEnvelopeDto(sessionId = id, scorecard = null, error = "kotlin_bridge_stub"))
        }
        get("/clips") {
            val id = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            call.respond(SessionClipsEnvelopeDto(sessionId = id, clips = emptyList(), count = 0))
        }
        get("/pedal_behavior") {
            val id = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            call.respond(
                PedalBehaviorDto(
                    sessionId = id,
                    frameCount = 0,
                    thresholds = PedalThresholdsDto(throttlePct = 50.0, brakeBar = 40.0),
                    frameDtS = 1.0 / 60.0,
                    states = emptyMap(),
                ),
            )
        }
        get("/straight_line_speed") {
            val id = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            call.respond(
                StraightLineSpeedDto(
                    sessionId = id,
                    trackLengthM = 4000.0,
                    straights = emptyList(),
                ),
            )
        }
        get("/brake_acceleration") {
            val id = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            call.respond(
                BrakeAccelerationDto(sessionId = id, brakeZones = emptyList(), cornerExits = emptyList()),
            )
        }
        get("/export.parquet") {
            call.respondBytes(ByteArray(0), ContentType.Application.OctetStream, HttpStatusCode.OK)
        }
    }

    post("/analyze") {
        val body = bridgeJson.decodeFromString(AnalyzeRequestDto.serializer(), call.receiveText())
        call.respond(
            AnalyzeResponseDto(
                coaching = "Kotlin bridge — connect LiteRT for live coaching.",
                paceNote = "straight",
                coachSource = "kotlin_stub",
                cues = null,
                burstId = body.burstId,
                source = "kotlin_bridge",
            ),
        )
    }

    get("/coach/brief") {
        val driver = call.request.queryParameters["driver"]
        val focus = call.request.queryParameters["focus"]
        val sessionId = call.request.queryParameters["session_id"]
        val brief: BriefResponseDto = inference.coachBrief(driver, focus, sessionId)
        call.respond(brief)
    }

    post("/coach/debrief") {
        call.respond(buildJsonObject { put("stub", true) })
    }

    post("/coach/ask") {
        val body = bridgeJson.decodeFromString(CoachAskRequestDto.serializer(), call.receiveText())
        val res: CoachAskResponseDto =
            inference.coachAsk(body.question, body.driverId, body.sessionId)
        call.respond(res)
    }

    post("/coach/ask/end") {
        val body = bridgeJson.decodeFromString(CoachAskEndRequestDto.serializer(), call.receiveText())
        call.respond(CoachAskEndResponseDto(flushed = 0, qaKey = "stub"))
    }

    post("/coach/ask/stream") {
        val body = bridgeJson.decodeFromString(CoachAskRequestDto.serializer(), call.receiveText())
        val res = inference.coachAsk(body.question, body.driverId, body.sessionId)
        call.respondOutputStream(ContentType.Text.EventStream) {
            val line =
                buildJsonObject {
                    put("done", JsonPrimitive(true))
                    put("answer", JsonPrimitive(res.answer ?: ""))
                    put("emotion", JsonPrimitive(res.emotion ?: "neutral"))
                }
            val jsonLine = bridgeJson.encodeToString(JsonObject.serializer(), line)
            write(("data: $jsonLine\n\n").toByteArray(Charsets.UTF_8))
            flush()
        }
    }

    get("/coach/concepts") {
        call.respond(
            CoachConceptsResponse(
                source = "kotlin_stub",
                concepts = emptyList<CoachConceptDto>(),
                count = 0,
            ),
        )
    }

    get("/coach/agents") {
        call.respond(buildJsonObject { put("agents", "kotlin_stub") })
    }

    post("/score") {
        val body = bridgeJson.decodeFromString(LlmScoreRequestDto.serializer(), call.receiveText())
        val out =
            inference.scoreSession(
                sessionId = body.sessionId,
                focus = body.focus,
                driverLevel = body.driverLevel,
            )
        call.respond(out)
    }

    get("/insights") {
        call.respond(
            InsightsResponseDto(
                insights = emptyList(),
                sessionBursts = 0,
                generatedAt = Instant.now().toString(),
            ),
        )
    }

    get("/cues/stream") {
        call.respondOutputStream(ContentType.Text.EventStream) {
            while (!Thread.currentThread().isInterrupted) {
                write(": ping\n\n".toByteArray(Charsets.UTF_8))
                flush()
                Thread.sleep(15_000)
            }
        }
    }

    get("/notifications") {
        call.respondOutputStream(ContentType.Text.EventStream) {
            while (!Thread.currentThread().isInterrupted) {
                write(": ping\n\n".toByteArray(Charsets.UTF_8))
                flush()
                Thread.sleep(30_000)
            }
        }
    }

    route("{tail...}") {
        handle {
            when (call.request.httpMethod) {
                HttpMethod.Get -> {
                    val path = call.request.path()
                    if (path.endsWith(".parquet")) {
                        call.respondBytes(ByteArray(0), ContentType.Application.OctetStream)
                    } else {
                        call.respondText("{}", ContentType.Application.Json)
                    }
                }
                HttpMethod.Post ->
                    call.respondText("{}", ContentType.Application.Json)
                HttpMethod.Put ->
                    call.respondText("{}", ContentType.Application.Json)
                else -> call.respond(HttpStatusCode.MethodNotAllowed)
            }
        }
    }
}
