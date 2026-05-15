package com.pitwall.bridge.ktor.embedded

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import kotlin.math.max

/**
 * Embedded DuckDB (JDBC) mirroring the Flask bridge catalog/tables needed for the PWA offline.
 */
class EmbeddedDuckDb(private val dbFile: File) {

    private val mutex = Mutex()
    private var connection: Connection? = null

    suspend fun <T> withConnection(block: Connection.() -> T): T = mutex.withLock {
        withContext(Dispatchers.IO) {
            val conn = connection ?: openAndMigrate().also { connection = it }
            conn.block()
        }
    }

    fun close() {
        connection?.close()
        connection = null
    }

    private fun openAndMigrate(): Connection {
        Log.i("EmbeddedDuckDb", "Opening Android SQLite at ${dbFile.absolutePath}")
        val conn = AndroidSqliteConnection.open(dbFile)
        migrate(conn)
        return conn
    }

    private fun migrate(c: Connection) {
        c.createStatement().use { st ->
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS laps (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id    TEXT,
                    lap_number    INTEGER,
                    lap_time_s    REAL,
                    best_sector   REAL,
                    avg_speed_kmh REAL,
                    max_combo_g   REAL,
                    coast_pct     REAL,
                    recorded_at   TEXT DEFAULT (datetime('now'))
                );
                CREATE TABLE IF NOT EXISTS coaching_notes (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id    TEXT,
                    burst_id      INTEGER,
                    distance_m    REAL,
                    text          TEXT,
                    source        TEXT,
                    recorded_at   TEXT DEFAULT (datetime('now'))
                );
                CREATE TABLE IF NOT EXISTS telemetry (
                    session_id   TEXT,
                    frame_idx    INTEGER,
                    timestamp    REAL,
                    distance_m   REAL,
                    speed_ms     REAL,
                    g_lat        REAL,
                    g_long       REAL,
                    combo_g      REAL,
                    brake_bar    REAL,
                    throttle_pct REAL,
                    steering_deg REAL,
                    rpm          REAL,
                    lat          REAL,
                    lon          REAL
                );
                CREATE INDEX IF NOT EXISTS idx_telemetry_session
                    ON telemetry(session_id, frame_idx);
                CREATE TABLE IF NOT EXISTS sessions (
                    session_id    TEXT PRIMARY KEY,
                    driver        TEXT,
                    driver_level  TEXT,
                    track         TEXT,
                    car           TEXT,
                    started_at    TEXT DEFAULT (datetime('now')),
                    ended_at      TEXT,
                    note          TEXT
                );
                CREATE TABLE IF NOT EXISTS signal_registry (
                    signal_id     INTEGER PRIMARY KEY AUTOINCREMENT,
                    name          TEXT UNIQUE NOT NULL,
                    units         TEXT,
                    semantics     TEXT,
                    "group"       TEXT,
                    expected_hz   REAL,
                    min_useful_hz REAL,
                    discovery     TEXT,
                    obd2_pid      TEXT,
                    discovered_at TEXT DEFAULT (datetime('now'))
                );
                CREATE TABLE IF NOT EXISTS telemetry_signals (
                    session_id  TEXT NOT NULL,
                    signal_id   INTEGER NOT NULL,
                    t           REAL NOT NULL,
                    value       REAL NOT NULL,
                    PRIMARY KEY (session_id, signal_id, t)
                );
                CREATE INDEX IF NOT EXISTS idx_signals_sess_sig_t
                    ON telemetry_signals (session_id, signal_id, t);
                CREATE TABLE IF NOT EXISTS session_capabilities (
                    session_id  TEXT NOT NULL,
                    signal_id   INTEGER NOT NULL,
                    n_samples   INTEGER NOT NULL,
                    mean_hz     REAL NOT NULL,
                    t_start     REAL NOT NULL,
                    t_end       REAL NOT NULL,
                    PRIMARY KEY (session_id, signal_id)
                );
                """.trimIndent(),
            )
        }
    }

    private val seedJson =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    /** Idempotent seed from Flask [`obd2_pids.json`](../../../../../data/registry/obd2_pids.json). */
    fun seedSignalRegistry(conn: Connection, jsonText: String): Int {
        val root = seedJson.parseToJsonElement(jsonText).jsonObject
        val arr = root["signals"]?.jsonArray ?: return 0
        val sql =
            """
            INSERT OR IGNORE INTO signal_registry
                (name, units, semantics, "group", expected_hz, min_useful_hz, discovery, obd2_pid)
            VALUES (?,?,?,?,?,?,?,?)
            """.trimIndent()
        var inserted = 0
        for (el in arr) {
            val o = el.jsonObject
            val name = o["name"]?.jsonPrimitive?.content ?: continue
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, name)
                setNullableString(ps, 2, o["units"]?.jsonPrimitive?.content)
                setNullableString(ps, 3, o["semantics"]?.jsonPrimitive?.content)
                setNullableString(ps, 4, o["group"]?.jsonPrimitive?.content)
                val exp = o["expected_hz"]?.jsonPrimitive?.doubleOrNull
                val minU = o["min_useful_hz"]?.jsonPrimitive?.doubleOrNull
                if (exp != null) ps.setDouble(5, exp) else ps.setNull(5, Types.DOUBLE)
                if (minU != null) ps.setDouble(6, minU) else ps.setNull(6, Types.DOUBLE)
                ps.setString(7, o["discovery"]?.jsonPrimitive?.content ?: "static_seed")
                setNullableString(ps, 8, o["obd2_pid"]?.jsonPrimitive?.content)
                val n = ps.executeUpdate()
                if (n > 0) inserted++
            }
        }
        return inserted
    }

    private fun setNullableString(ps: PreparedStatement, idx: Int, s: String?) {
        if (s == null) ps.setNull(idx, Types.VARCHAR) else ps.setString(idx, s)
    }

    fun resolveSignalId(conn: Connection, name: String): Int {
        conn.prepareStatement("SELECT signal_id FROM signal_registry WHERE name = ?").use { ps ->
            ps.setString(1, name)
            ps.executeQuery().use { rs ->
                if (rs.next()) return rs.getInt(1)
            }
        }
        conn.prepareStatement(
            """
            INSERT INTO signal_registry (name, discovery)
            VALUES (?, 'discovered')
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, name)
            ps.executeUpdate()
        }
        conn.prepareStatement("SELECT signal_id FROM signal_registry WHERE name = ?").use { ps ->
            ps.setString(1, name)
            ps.executeQuery().use { rs ->
                if (rs.next()) return rs.getInt(1)
            }
        }
        throw IllegalStateException("signal_registry: failed to resolve $name")
    }

    fun appendTelemetrySignals(
        conn: Connection,
        sessionId: String,
        samples: List<Triple<String, Double, Double>>,
    ): Int {
        if (samples.isEmpty()) return 0
        val sql =
            """
            INSERT INTO telemetry_signals VALUES (?,?,?,?)
            ON CONFLICT (session_id, signal_id, t) DO UPDATE SET value = excluded.value
            """.trimIndent()
        var n = 0
        for ((sigName, t, v) in samples) {
            val sid = resolveSignalId(conn, sigName)
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, sessionId)
                ps.setInt(2, sid)
                ps.setDouble(3, t)
                ps.setDouble(4, v)
                ps.executeUpdate()
                n++
            }
        }
        return n
    }

    /**
     * Rewrites [`session_capabilities`](../../../../../src/pitwall/db.py) for one session from
     * wide [`telemetry`] plus grouped [`telemetry_signals`] — mirrors Flask `compute_capabilities`.
     */
    fun computeCapabilities(conn: Connection, sessionId: String): Int {
        conn.prepareStatement("DELETE FROM session_capabilities WHERE session_id = ?").use { ps ->
            ps.setString(1, sessionId)
            ps.executeUpdate()
        }
        var rowsWritten = 0

        conn.prepareStatement(
            "SELECT COUNT(*), MIN(timestamp), MAX(timestamp) FROM telemetry WHERE session_id = ?",
        ).use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return@use
                val n = rs.getLong(1)
                val tStart = rs.getObject(2) as? Double
                val tEnd = rs.getObject(3) as? Double
                if (n <= 0L || tStart == null || tEnd == null) return@use
                val duration = max(tEnd - tStart, 1e-6)
                val meanHz = n.toDouble() / duration
                val placeholders = EMBEDDED_WIDE_SIGNAL_NAMES.joinToString(",") { "?" }
                val sigSql =
                    "SELECT signal_id FROM signal_registry WHERE name IN ($placeholders)"
                conn.prepareStatement(sigSql).use { sigPs ->
                    EMBEDDED_WIDE_SIGNAL_NAMES.forEachIndexed { i, name ->
                        sigPs.setString(i + 1, name)
                    }
                    sigPs.executeQuery().use { srs ->
                        while (srs.next()) {
                            val sigId = srs.getInt(1)
                            conn.prepareStatement(
                                "INSERT INTO session_capabilities VALUES (?,?,?,?,?,?)",
                            ).use { ins ->
                                ins.setString(1, sessionId)
                                ins.setInt(2, sigId)
                                ins.setLong(3, n)
                                ins.setDouble(4, meanHz)
                                ins.setDouble(5, tStart)
                                ins.setDouble(6, tEnd)
                                ins.executeUpdate()
                                rowsWritten++
                            }
                        }
                    }
                }
            }
        }

        conn.prepareStatement(
            """
            SELECT signal_id, COUNT(*), MIN(t), MAX(t)
            FROM telemetry_signals
            WHERE session_id = ?
            GROUP BY signal_id
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val sigId = rs.getInt(1)
                    val ns = rs.getLong(2)
                    val ts = rs.getDouble(3)
                    val te = rs.getDouble(4)
                    val duration = max(te - ts, 1e-6)
                    val hz = ns.toDouble() / duration
                    conn.prepareStatement(
                        """
                        INSERT INTO session_capabilities VALUES (?,?,?,?,?,?)
                        ON CONFLICT (session_id, signal_id) DO UPDATE SET
                            n_samples = excluded.n_samples,
                            mean_hz = excluded.mean_hz,
                            t_start = excluded.t_start,
                            t_end = excluded.t_end
                        """.trimIndent(),
                    ).use { ups ->
                        ups.setString(1, sessionId)
                        ups.setInt(2, sigId)
                        ups.setLong(3, ns)
                        ups.setDouble(4, hz)
                        ups.setDouble(5, ts)
                        ups.setDouble(6, te)
                        ups.executeUpdate()
                        rowsWritten++
                    }
                }
            }
        }
        return rowsWritten
    }

    fun querySignalsRegistry(conn: Connection): JsonArray {
        conn.prepareStatement(
            """
            SELECT signal_id, name, units, semantics, "group",
                   expected_hz, min_useful_hz, discovery, obd2_pid
            FROM signal_registry ORDER BY "group", name
            """.trimIndent(),
        ).use { ps ->
            ps.executeQuery().use { rs ->
                return buildJsonArray {
                    while (rs.next()) {
                        add(
                            buildJsonObject {
                                put("signal_id", JsonPrimitive(rs.getInt(1)))
                                put("name", JsonPrimitive(rs.getString(2)))
                                put("units", rs.getString(3)?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("semantics", rs.getString(4)?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("group", rs.getString(5)?.let { JsonPrimitive(it) } ?: JsonNull)
                                val eh = rs.getObject(6) as Double?
                                put("expected_hz", if (eh != null) JsonPrimitive(eh) else JsonNull)
                                val mu = rs.getObject(7) as Double?
                                put("min_useful_hz", if (mu != null) JsonPrimitive(mu) else JsonNull)
                                put("discovery", rs.getString(8)?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("obd2_pid", rs.getString(9)?.let { JsonPrimitive(it) } ?: JsonNull)
                            },
                        )
                    }
                }
            }
        }
    }

    /** Flask `GET /session/<sid>/capabilities` (coaches_* left empty on embedded). */
    fun querySessionCapabilitiesJson(conn: Connection, sessionId: String): JsonObject? {
        data class CapRow(
            val name: String,
            val nSamples: Long,
            val meanHz: Double,
            val minUseful: Double?,
            val tStart: Double,
            val tEnd: Double,
        )
        val rows = ArrayList<CapRow>()
        conn.prepareStatement(
            """
            SELECT sr.name, sc.n_samples, sc.mean_hz, sr.min_useful_hz, sc.t_start, sc.t_end
            FROM session_capabilities sc
            JOIN signal_registry sr USING(signal_id)
            WHERE sc.session_id = ?
            ORDER BY sr.name
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    rows.add(
                        CapRow(
                            name = rs.getString(1),
                            nSamples = rs.getLong(2),
                            meanHz = rs.getDouble(3),
                            minUseful = rs.getObject(4) as Double?,
                            tStart = rs.getDouble(5),
                            tEnd = rs.getDouble(6),
                        ),
                    )
                }
            }
        }
        if (rows.isEmpty()) return null
        val tStarts = rows.map { it.tStart }
        val tEnds = rows.map { it.tEnd }
        val durationS = if (tStarts.isNotEmpty()) (tEnds.maxOrNull()!! - tStarts.minOrNull()!!) else 0.0
        val signalsArr = buildJsonArray {
            for (r in rows) {
                val useful = r.minUseful == null || r.meanHz >= r.minUseful
                add(
                    buildJsonObject {
                        put("name", JsonPrimitive(r.name))
                        put("n_samples", JsonPrimitive(r.nSamples.toInt()))
                        put("mean_hz", JsonPrimitive(r.meanHz))
                        put("useful", JsonPrimitive(useful))
                    },
                )
            }
        }
        return buildJsonObject {
            put("session_id", JsonPrimitive(sessionId))
            put("duration_s", JsonPrimitive(durationS))
            put("signals", signalsArr)
            put("coaches_available", JsonArray(emptyList()))
            put("coaches_disabled", JsonArray(emptyList()))
        }
    }

    /**
     * Simplified Flask `GET /session/<sid>/signals`: wide-table columns + tall store,
     * axis `time`/`gps`/`t` from telemetry timestamps; `interp` hold or lerp on aligned axis_ts.
     */
    fun querySessionSignalsJson(
        conn: Connection,
        sessionId: String,
        names: List<String>,
        axis: String,
        interpKind: String,
        rateHz: Double,
        tFromIn: Double?,
        tToIn: Double?,
    ): JsonObject? {
        val nWide =
            conn.prepareStatement("SELECT COUNT(*) FROM telemetry WHERE session_id = ?").use { ps ->
                ps.setString(1, sessionId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
            }
        val nTall =
            conn.prepareStatement("SELECT COUNT(*) FROM telemetry_signals WHERE session_id = ?").use { ps ->
                ps.setString(1, sessionId)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
            }
        if (nWide == 0L && nTall == 0L) return null

        var tFrom = tFromIn
        var tTo = tToIn
        if (tFrom == null || tTo == null) {
            val bounds = mutableListOf<Pair<Double, Double>>()
            conn.prepareStatement(
                "SELECT MIN(timestamp), MAX(timestamp) FROM telemetry WHERE session_id = ?",
            ).use { ps ->
                ps.setString(1, sessionId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        val a = rs.getObject(1) as? Double
                        val b = rs.getObject(2) as? Double
                        if (a != null && b != null) bounds.add(a to b)
                    }
                }
            }
            conn.prepareStatement(
                "SELECT MIN(t), MAX(t) FROM telemetry_signals WHERE session_id = ?",
            ).use { ps ->
                ps.setString(1, sessionId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        val a = rs.getObject(1) as? Double
                        val b = rs.getObject(2) as? Double
                        if (a != null && b != null) bounds.add(a to b)
                    }
                }
            }
            if (bounds.isNotEmpty()) {
                if (tFrom == null) tFrom = bounds.minOf { it.first }
                if (tTo == null) tTo = bounds.maxOf { it.second }
            }
        }
        if (tFrom == null || tTo == null) return null

        val axisTs =
            when {
                rateHz > 0 -> {
                    val step = 1.0 / rateHz
                    val out = ArrayList<Double>()
                    var t = tFrom
                    while (t <= tTo + 1e-9) {
                        out.add(t)
                        t += step
                    }
                    out
                }
                axis.lowercase() in setOf("gps", "time", "t") -> {
                    val sql =
                        buildString {
                            append("SELECT DISTINCT timestamp FROM telemetry WHERE session_id = ?")
                            append(" AND timestamp >= ? AND timestamp <= ? ORDER BY timestamp")
                        }
                    conn.prepareStatement(sql).use { ps ->
                        ps.setString(1, sessionId)
                        ps.setDouble(2, tFrom)
                        ps.setDouble(3, tTo)
                        ps.executeQuery().use { rs ->
                            val out = ArrayList<Double>()
                            while (rs.next()) out.add(rs.getDouble(1))
                            out
                        }
                    }
                }
                else ->
                    return buildJsonObject {
                        put("error", JsonPrimitive("embedded: axis must be gps, time, t, or rate_hz>0"))
                    }
            }
        if (axisTs.isEmpty()) {
            return buildJsonObject {
                put("session_id", JsonPrimitive(sessionId))
                put("axis", JsonPrimitive(axis))
                put("rate_hz", JsonPrimitive(rateHz))
                put("interp", JsonPrimitive(interpKind))
                put("t_from", JsonPrimitive(tFrom))
                put("t_to", JsonPrimitive(tTo))
                put("names", JsonArray(names.map { JsonPrimitive(it) }))
                put("rows", JsonArray(emptyList()))
                put("missing", JsonArray(emptyList()))
                put("count", JsonPrimitive(0))
            }
        }

        val wideCol =
            mapOf(
                "distance_m" to "distance_m",
                "speed_ms" to "speed_ms",
                "g_lat" to "g_lat",
                "g_long" to "g_long",
                "combo_g" to "combo_g",
                "brake_bar" to "brake_bar",
                "throttle_pct" to "throttle_pct",
                "steering_deg" to "steering_deg",
                "rpm" to "rpm",
                "lat" to "lat",
                "lon" to "lon",
            )

        for (nm in names) {
            if (wideCol.containsKey(nm)) continue
            conn.prepareStatement("SELECT 1 FROM signal_registry WHERE name = ? LIMIT 1").use { ps ->
                ps.setString(1, nm)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) {
                        return buildJsonObject {
                            put("error", JsonPrimitive("unknown signal: $nm"))
                        }
                    }
                }
            }
        }

        val series = LinkedHashMap<String, List<Pair<Double, Double>>>()
        val missing = ArrayList<String>()
        for (nm in names) {
            val col = wideCol[nm]
            if (col != null) {
                val pts = ArrayList<Pair<Double, Double>>()
                conn.prepareStatement(
                    "SELECT timestamp, $col FROM telemetry WHERE session_id = ? AND timestamp >= ? AND timestamp <= ? ORDER BY timestamp",
                ).use { ps ->
                    ps.setString(1, sessionId)
                    ps.setDouble(2, tFrom)
                    ps.setDouble(3, tTo)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            pts.add(rs.getDouble(1) to rs.getDouble(2))
                        }
                    }
                }
                series[nm] = pts
                if (pts.isEmpty()) missing.add(nm)
            } else {
                val sid =
                    conn.prepareStatement("SELECT signal_id FROM signal_registry WHERE name = ?").use { ps ->
                        ps.setString(1, nm)
                        ps.executeQuery().use { rs ->
                            if (rs.next()) rs.getInt(1) else null
                        }
                    }
                if (sid == null) {
                    return buildJsonObject {
                        put("error", JsonPrimitive("unknown signal: $nm"))
                    }
                }
                val pts = ArrayList<Pair<Double, Double>>()
                conn.prepareStatement(
                    """
                    SELECT t, value FROM telemetry_signals
                    WHERE session_id = ? AND signal_id = ? AND t >= ? AND t <= ? ORDER BY t
                    """.trimIndent(),
                ).use { ps ->
                    ps.setString(1, sessionId)
                    ps.setInt(2, sid)
                    ps.setDouble(3, tFrom)
                    ps.setDouble(4, tTo)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            pts.add(rs.getDouble(1) to rs.getDouble(2))
                        }
                    }
                }
                series[nm] = pts
                if (pts.isEmpty()) missing.add(nm)
            }
        }

        fun interpAt(t: Double, pts: List<Pair<Double, Double>>): Double? {
            if (pts.isEmpty()) return null
            if (pts.size == 1) return pts[0].second
            var lo = 0
            var hi = pts.size - 1
            if (t <= pts[lo].first) return pts[lo].second
            if (t >= pts[hi].first) return pts[hi].second
            while (hi - lo > 1) {
                val mid = (lo + hi) / 2
                if (pts[mid].first <= t) lo = mid else hi = mid
            }
            val (t0, v0) = pts[lo]
            val (t1, v1) = pts[hi]
            return when (interpKind.lowercase()) {
                "lerp" -> v0 + (v1 - v0) * ((t - t0) / (t1 - t0).coerceAtLeast(1e-12))
                else -> v0
            }
        }

        val rowsOut = buildJsonArray {
            for (at in axisTs) {
                add(
                    buildJsonObject {
                        put("t", JsonPrimitive(at))
                        for (nm in names) {
                            val pts = series[nm].orEmpty()
                            put(nm, interpAt(at, pts)?.let { JsonPrimitive(it) } ?: JsonNull)
                        }
                    },
                )
            }
        }
        return buildJsonObject {
            put("session_id", JsonPrimitive(sessionId))
            put("axis", JsonPrimitive(axis))
            put("rate_hz", JsonPrimitive(rateHz))
            put("interp", JsonPrimitive(interpKind))
            put("t_from", JsonPrimitive(tFrom))
            put("t_to", JsonPrimitive(tTo))
            put("names", JsonArray(names.map { JsonPrimitive(it) }))
            put("rows", rowsOut)
            put("missing", JsonArray(missing.map { JsonPrimitive(it) }))
            put("count", JsonPrimitive(axisTs.size))
        }
    }

    fun countTelemetry(conn: Connection, sessionId: String): Long =
        conn.prepareStatement("SELECT COUNT(*) FROM telemetry WHERE session_id = ?").use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs ->
                if (rs.next()) rs.getLong(1) else 0L
            }
        }

    fun sessionExists(conn: Connection, sessionId: String): Boolean =
        conn.prepareStatement("SELECT 1 FROM sessions WHERE session_id = ? LIMIT 1").use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { it.next() }
        }

    fun appendFrames(
        conn: Connection,
        sessionId: String,
        frames: List<FrameRow>,
    ): Int {
        if (frames.isEmpty()) return 0
        val existing = conn.prepareStatement(
            "SELECT COALESCE(MAX(frame_idx), -1) FROM telemetry WHERE session_id = ?",
        ).use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else -1 }
        }
        var idx = existing + 1
        val sql =
            """
            INSERT INTO telemetry VALUES (
              ?,?,?,?,?,?,?,?,?,?,?,?,?,?
            )
            """.trimIndent()
        for (f in frames) {
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, sessionId)
                ps.setInt(2, idx++)
                ps.setDouble(3, f.timestamp)
                ps.setDouble(4, f.distanceM)
                ps.setDouble(5, f.speedMs)
                ps.setDouble(6, f.gLat)
                ps.setDouble(7, f.gLong)
                ps.setDouble(8, f.comboG)
                ps.setDouble(9, f.brakeBar)
                ps.setDouble(10, f.throttlePct)
                ps.setDouble(11, f.steeringDeg)
                ps.setDouble(12, f.rpm)
                ps.setDouble(13, f.lat)
                ps.setDouble(14, f.lon)
                ps.executeUpdate()
            }
        }
        return frames.size
    }

    fun insertCoachingNote(
        conn: Connection,
        sessionId: String,
        burstId: Int,
        distanceM: Double,
        text: String,
        source: String,
    ) {
        conn.prepareStatement(
            """
            INSERT INTO coaching_notes (session_id, burst_id, distance_m, text, source)
            VALUES (?,?,?,?,?)
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, sessionId)
            ps.setInt(2, burstId)
            ps.setDouble(3, distanceM)
            ps.setString(4, text)
            ps.setString(5, source)
            ps.executeUpdate()
        }
    }

    fun exportTelemetryParquet(conn: Connection, sessionId: String, outFile: File) {
        // DuckDB COPY...PARQUET not available on Android SQLite; export as CSV instead
        conn.prepareStatement(
            "SELECT * FROM telemetry WHERE session_id = ? ORDER BY frame_idx",
        ).use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs ->
                outFile.bufferedWriter().use { w ->
                    val meta = rs.metaData
                    val cols = (1..meta.columnCount).map { meta.getColumnLabel(it) }
                    w.write(cols.joinToString(","))
                    w.newLine()
                    while (rs.next()) {
                        w.write(cols.indices.joinToString(",") { i ->
                            rs.getString(i + 1) ?: ""
                        })
                        w.newLine()
                    }
                }
            }
        }
    }

    data class FrameRow(
        val timestamp: Double,
        val distanceM: Double,
        val speedMs: Double,
        val gLat: Double,
        val gLong: Double,
        val comboG: Double,
        val brakeBar: Double,
        val throttlePct: Double,
        val steeringDeg: Double,
        val rpm: Double,
        val lat: Double,
        val lon: Double,
    )

    data class LapRow(
        val lapNumber: Int,
        val lapTimeS: Double?,
        val bestSector: Double?,
        val avgSpeedKmh: Double?,
        val maxComboG: Double?,
        val coastPct: Double?,
        val recordedAt: String?,
    )

    fun listLaps(conn: Connection, sessionId: String): List<LapRow> {
        val out = ArrayList<LapRow>()
        conn.prepareStatement(
            """
            SELECT lap_number, lap_time_s, best_sector, avg_speed_kmh, max_combo_g, coast_pct, recorded_at
            FROM laps WHERE session_id = ? ORDER BY lap_number ASC
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val ts = rs.getTimestamp(7)
                    out.add(
                        LapRow(
                            lapNumber = rs.getInt(1),
                            lapTimeS = rs.getObject(2) as Double?,
                            bestSector = rs.getObject(3) as Double?,
                            avgSpeedKmh = rs.getObject(4) as Double?,
                            maxComboG = rs.getObject(5) as Double?,
                            coastPct = rs.getObject(6) as Double?,
                            recordedAt = ts?.toInstant()?.toString(),
                        ),
                    )
                }
            }
        }
        return out
    }

    fun pedalBehaviorRows(conn: Connection, sessionId: String): List<Triple<Double, Double, Double>> {
        val rows = ArrayList<Triple<Double, Double, Double>>()
        conn.prepareStatement(
            "SELECT timestamp, throttle_pct, brake_bar FROM telemetry WHERE session_id = ? ORDER BY timestamp",
        ).use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    rows.add(Triple(rs.getDouble(1), rs.getDouble(2), rs.getDouble(3)))
                }
            }
        }
        return rows
    }

    /**
     * `GET /laps` — mirror Flask `SELECT * FROM laps` with optional session filter.
     */
    fun queryLapsListing(conn: Connection, sessionId: String?, limit: Int): JsonArray {
        val lim = limit.coerceIn(1, 500)
        val sql = buildString {
            append("SELECT * FROM laps")
            if (sessionId != null) append(" WHERE session_id = ?")
            append(" ORDER BY recorded_at DESC LIMIT ?")
        }
        return conn.prepareStatement(sql).use { ps ->
            var i = 1
            if (sessionId != null) ps.setString(i++, sessionId)
            ps.setInt(i, lim)
            ps.executeQuery().use { rs ->
                buildJsonArray {
                    while (rs.next()) {
                        add(rowToJsonObject(rs))
                    }
                }
            }
        }
    }

    private fun rowToJsonObject(rs: ResultSet): JsonObject {
        val meta = rs.metaData
        return buildJsonObject {
            for (i in 1..meta.columnCount) {
                val name = meta.getColumnLabel(i)
                if (rs.getObject(i) == null) {
                    put(name, JsonNull)
                    continue
                }
                when (meta.getColumnType(i)) {
                    Types.BOOLEAN -> put(name, JsonPrimitive(rs.getBoolean(i)))
                    Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT ->
                        put(name, JsonPrimitive(rs.getLong(i)))
                    Types.FLOAT, Types.DOUBLE, Types.REAL, Types.DECIMAL, Types.NUMERIC ->
                        put(name, JsonPrimitive(rs.getDouble(i)))
                    Types.TIMESTAMP, Types.DATE, Types.TIME ->
                        put(name, JsonPrimitive(rs.getTimestamp(i)?.toInstant()?.toString()))
                    else -> put(name, JsonPrimitive(rs.getString(i) ?: ""))
                }
            }
        }
    }

    internal fun loadTelemetryOrdered(conn: Connection, sessionId: String): List<EmbeddedLapEngine.TelemetrySample> {
        val out = ArrayList<EmbeddedLapEngine.TelemetrySample>()
        conn.prepareStatement(
            "SELECT timestamp, distance_m, lat, lon FROM telemetry WHERE session_id = ? ORDER BY frame_idx",
        ).use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    out.add(
                        EmbeddedLapEngine.TelemetrySample(
                            timestamp = rs.getDouble(1),
                            distanceM = rs.getObject(2) as Double?,
                            lat = rs.getObject(3) as Double?,
                            lon = rs.getObject(4) as Double?,
                        ),
                    )
                }
            }
        }
        return out
    }

    internal fun countLaps(conn: Connection, sessionId: String): Int =
        conn.prepareStatement("SELECT COUNT(*) FROM laps WHERE session_id = ?").use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }

    internal fun insertDetectedLaps(conn: Connection, sessionId: String, laps: List<EmbeddedLapEngine.DetectedLap>) {
        for (lap in laps) {
            conn.prepareStatement(
                """
                INSERT INTO laps (session_id, lap_number, lap_time_s, best_sector, avg_speed_kmh, max_combo_g, coast_pct)
                VALUES (?,?,?,?,?,?,?)
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, sessionId)
                ps.setInt(2, lap.lapNumber)
                ps.setDouble(3, lap.lapTimeS)
                ps.setDouble(4, 0.0)
                ps.setDouble(5, 0.0)
                ps.setDouble(6, 0.0)
                ps.setDouble(7, 0.0)
                ps.executeUpdate()
            }
        }
    }

    private companion object {
        /** Matches Flask [`WIDE_SIGNAL_NAMES`](../../../../../src/pitwall/db.py). */
        val EMBEDDED_WIDE_SIGNAL_NAMES: List<String> =
            listOf(
                "distance_m",
                "speed_ms",
                "g_lat",
                "g_long",
                "combo_g",
                "brake_bar",
                "throttle_pct",
                "steering_deg",
                "rpm",
                "lat",
                "lon",
            )
    }
}
