package com.pitwall.bridge.ktor.embedded

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal fun newSessionId(track: String?): String {
    val slug = (track ?: "session").lowercase().replace(' ', '-').replace(Regex("[^a-z0-9_.:-]+"), "")
    val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now())
    return "$slug-$stamp"
}

internal fun isSafeSessionId(id: String): Boolean =
    id.isNotBlank() && id.length <= 128 && id.matches(Regex("^[a-zA-Z0-9_.:-]+$"))

/**
 * Session CRUD + lap insert mirroring Flask `bp_session` / `bp_track` shapes for JSON responses.
 */
class EmbeddedSessionRepository(private val duck: EmbeddedDuckDb) {

    suspend fun ensureSession(
        sessionId: String,
        driver: String?,
        driverLevel: String?,
        track: String?,
        car: String?,
        note: String?,
    ) {
        duck.withConnection {
            prepareStatement(
                """
                INSERT INTO sessions (session_id, driver, driver_level, track, car, note)
                VALUES (?,?,?,?,?,?)
                ON CONFLICT (session_id) DO UPDATE SET
                  driver = COALESCE(excluded.driver, sessions.driver),
                  driver_level = COALESCE(excluded.driver_level, sessions.driver_level),
                  track = COALESCE(excluded.track, sessions.track),
                  car = COALESCE(excluded.car, sessions.car),
                  note = COALESCE(excluded.note, sessions.note)
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, sessionId)
                ps.setString(2, driver ?: "")
                ps.setString(3, driverLevel ?: "intermediate")
                ps.setString(4, track ?: "")
                ps.setString(5, car ?: "")
                ps.setString(6, note ?: "")
                ps.executeUpdate()
            }
        }
    }

    suspend fun endSession(sessionId: String) {
        duck.withConnection {
            val exists = duck.sessionExists(this, sessionId)
            if (!exists) {
                prepareStatement(
                    "INSERT INTO sessions (session_id, ended_at) VALUES (?, datetime('now'))",
                ).use { ps ->
                    ps.setString(1, sessionId)
                    ps.executeUpdate()
                }
            } else {
                prepareStatement(
                    "UPDATE sessions SET ended_at = datetime('now') WHERE session_id = ? AND ended_at IS NULL",
                ).use { ps ->
                    ps.setString(1, sessionId)
                    ps.executeUpdate()
                }
            }
        }
    }

    suspend fun listSessions(limit: Int, activeOnly: Boolean): JsonObject = duck.withConnection {
        val sql = buildString {
            append(
                """
                SELECT s.session_id, s.driver, s.driver_level, s.track, s.car, s.started_at, s.ended_at, s.note,
                       (SELECT COUNT(*) FROM laps l WHERE l.session_id = s.session_id),
                       (SELECT MIN(lap_time_s) FROM laps l WHERE l.session_id = s.session_id)
                FROM sessions s
                """.trimIndent(),
            )
            if (activeOnly) append(" WHERE s.ended_at IS NULL")
            append(" ORDER BY s.started_at DESC LIMIT ?")
        }
        prepareStatement(sql).use { ps ->
            ps.setInt(1, limit.coerceIn(1, 500))
            ps.executeQuery().use { rs ->
                val sessions = buildJsonArray {
                    while (rs.next()) {
                        val sid = rs.getString(1)
                        val lapCount = rs.getLong(9)
                        val bestLap = rs.getObject(10) as Double?
                        add(
                            buildJsonObject {
                                put("session_id", JsonPrimitive(sid))
                                put("driver", JsonPrimitive(rs.getString(2) ?: ""))
                                put("driver_level", JsonPrimitive(rs.getString(3) ?: ""))
                                put("track", JsonPrimitive(rs.getString(4) ?: ""))
                                put("car", JsonPrimitive(rs.getString(5) ?: ""))
                                put(
                                    "started_at",
                                    JsonPrimitive(rs.getTimestamp(6)?.toInstant()?.toString()),
                                )
                                put(
                                    "ended_at",
                                    JsonPrimitive(rs.getTimestamp(7)?.toInstant()?.toString()),
                                )
                                put("note", JsonPrimitive(rs.getString(8) ?: ""))
                                put("lap_count", JsonPrimitive(lapCount.toInt()))
                                put(
                                    "best_lap_s",
                                    if (bestLap != null) JsonPrimitive(bestLap) else JsonNull,
                                )
                            },
                        )
                    }
                }
                buildJsonObject {
                    put("sessions", sessions)
                    put("count", JsonPrimitive(sessions.size))
                }
            }
        }
    }

    suspend fun sessionDetail(sessionId: String): JsonObject? {
        val sessionObj = duck.withConnection {
            prepareStatement(
                "SELECT session_id, driver, driver_level, track, car, started_at, ended_at, note FROM sessions WHERE session_id = ?",
            ).use { ps ->
                ps.setString(1, sessionId)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) return@withConnection null
                    buildJsonObject {
                        put("session_id", JsonPrimitive(rs.getString(1)))
                        put("driver", JsonPrimitive(rs.getString(2) ?: ""))
                        put("driver_level", JsonPrimitive(rs.getString(3) ?: ""))
                        put("track", JsonPrimitive(rs.getString(4) ?: ""))
                        put("car", JsonPrimitive(rs.getString(5) ?: ""))
                        put("started_at", JsonPrimitive(rs.getTimestamp(6)?.toInstant()?.toString()))
                        put("ended_at", JsonPrimitive(rs.getTimestamp(7)?.toInstant()?.toString()))
                        put("note", JsonPrimitive(rs.getString(8) ?: ""))
                    }
                }
            }
        } ?: return null

        val laps = duck.withConnection {
            buildJsonArray {
                prepareStatement(
                    """
                    SELECT lap_number, lap_time_s, best_sector, avg_speed_kmh, max_combo_g, coast_pct, recorded_at
                    FROM laps WHERE session_id = ? ORDER BY lap_number ASC
                    """.trimIndent(),
                ).use { lps ->
                    lps.setString(1, sessionId)
                    lps.executeQuery().use { lrs ->
                        while (lrs.next()) {
                            add(
                                buildJsonObject {
                                    put("lap_number", JsonPrimitive(lrs.getInt(1)))
                                    put(
                                        "lap_time_s",
                                        if (lrs.getObject(2) != null) JsonPrimitive(lrs.getDouble(2)) else JsonNull,
                                    )
                                    put(
                                        "best_sector",
                                        if (lrs.getObject(3) != null) JsonPrimitive(lrs.getDouble(3)) else JsonNull,
                                    )
                                    put(
                                        "avg_speed_kmh",
                                        if (lrs.getObject(4) != null) JsonPrimitive(lrs.getDouble(4)) else JsonNull,
                                    )
                                    put(
                                        "max_combo_g",
                                        if (lrs.getObject(5) != null) JsonPrimitive(lrs.getDouble(5)) else JsonNull,
                                    )
                                    put(
                                        "coast_pct",
                                        if (lrs.getObject(6) != null) JsonPrimitive(lrs.getDouble(6)) else JsonNull,
                                    )
                                    put(
                                        "recorded_at",
                                        JsonPrimitive(lrs.getTimestamp(7)?.toInstant()?.toString()),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        val notes = duck.withConnection {
            buildJsonArray {
                prepareStatement(
                    """
                    SELECT burst_id, distance_m, text, source, recorded_at
                    FROM coaching_notes WHERE session_id = ?
                    ORDER BY recorded_at DESC LIMIT 50
                    """.trimIndent(),
                ).use { nps ->
                    nps.setString(1, sessionId)
                    nps.executeQuery().use { nrs ->
                        while (nrs.next()) {
                            add(
                                buildJsonObject {
                                    put("burst_id", JsonPrimitive(nrs.getInt(1)))
                                    put("distance_m", JsonPrimitive(nrs.getDouble(2)))
                                    put("text", JsonPrimitive(nrs.getString(3) ?: ""))
                                    put("source", JsonPrimitive(nrs.getString(4) ?: ""))
                                    put(
                                        "recorded_at",
                                        JsonPrimitive(nrs.getTimestamp(5)?.toInstant()?.toString()),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        val lapTimes = duck.withConnection {
            val times = mutableListOf<Double>()
            prepareStatement(
                "SELECT lap_time_s FROM laps WHERE session_id = ? AND lap_time_s IS NOT NULL",
            ).use { tps ->
                tps.setString(1, sessionId)
                tps.executeQuery().use { trs ->
                    while (trs.next()) times.add(trs.getDouble(1))
                }
            }
            times
        }
        val best = lapTimes.minOrNull()

        return buildJsonObject {
            put("session", sessionObj)
            put("laps", laps)
            put("notes", notes)
            put("lap_count", JsonPrimitive(laps.size))
            put("best_lap_s", if (best != null) JsonPrimitive(best) else JsonNull)
        }
    }

    suspend fun saveLap(body: JsonObject): Boolean {
        val sid = body["session_id"]?.let { (it as? JsonPrimitive)?.content } ?: return false
        if (!isSafeSessionId(sid)) return false
        val lapNumber = body["lap_number"]?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() } ?: 0
        val lapTime = body["lap_time_s"]?.let { (it as? JsonPrimitive)?.content?.toDoubleOrNull() } ?: 0.0
        val bestSector = body["best_sector"]?.let { (it as? JsonPrimitive)?.content?.toDoubleOrNull() } ?: 0.0
        val avgSpeed = body["avg_speed_kmh"]?.let { (it as? JsonPrimitive)?.content?.toDoubleOrNull() } ?: 0.0
        val maxG = body["max_combo_g"]?.let { (it as? JsonPrimitive)?.content?.toDoubleOrNull() } ?: 0.0
        val coast = body["coast_pct"]?.let { (it as? JsonPrimitive)?.content?.toDoubleOrNull() } ?: 0.0
        duck.withConnection {
            prepareStatement(
                """
                INSERT INTO laps (session_id, lap_number, lap_time_s, best_sector, avg_speed_kmh, max_combo_g, coast_pct)
                VALUES (?,?,?,?,?,?,?)
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, sid)
                ps.setInt(2, lapNumber)
                ps.setDouble(3, lapTime)
                ps.setDouble(4, bestSector)
                ps.setDouble(5, avgSpeed)
                ps.setDouble(6, maxG)
                ps.setDouble(7, coast)
                ps.executeUpdate()
            }
        }
        return true
    }
}
