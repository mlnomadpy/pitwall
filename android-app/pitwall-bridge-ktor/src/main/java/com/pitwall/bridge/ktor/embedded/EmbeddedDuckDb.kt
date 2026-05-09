package com.pitwall.bridge.ktor.embedded

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Types

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
        dbFile.parentFile?.mkdirs()
        Class.forName("org.duckdb.DuckDBDriver")
        val conn = DriverManager.getConnection("jdbc:duckdb:${dbFile.absolutePath}")
        conn.autoCommit = true
        migrate(conn)
        return conn
    }

    private fun migrate(c: Connection) {
        c.createStatement().use { st ->
            st.execute(
                """
                CREATE SEQUENCE IF NOT EXISTS laps_id_seq;
                CREATE TABLE IF NOT EXISTS laps (
                    id            INTEGER PRIMARY KEY DEFAULT nextval('laps_id_seq'),
                    session_id    VARCHAR,
                    lap_number    INTEGER,
                    lap_time_s    DOUBLE,
                    best_sector   DOUBLE,
                    avg_speed_kmh DOUBLE,
                    max_combo_g   DOUBLE,
                    coast_pct     DOUBLE,
                    recorded_at   TIMESTAMP DEFAULT now()
                );
                CREATE SEQUENCE IF NOT EXISTS notes_id_seq;
                CREATE TABLE IF NOT EXISTS coaching_notes (
                    id            INTEGER PRIMARY KEY DEFAULT nextval('notes_id_seq'),
                    session_id    VARCHAR,
                    burst_id      INTEGER,
                    distance_m    DOUBLE,
                    text          VARCHAR,
                    source        VARCHAR,
                    recorded_at   TIMESTAMP DEFAULT now()
                );
                CREATE TABLE IF NOT EXISTS telemetry (
                    session_id   VARCHAR,
                    frame_idx    INTEGER,
                    timestamp    DOUBLE,
                    distance_m   DOUBLE,
                    speed_ms     DOUBLE,
                    g_lat        DOUBLE,
                    g_long       DOUBLE,
                    combo_g      DOUBLE,
                    brake_bar    DOUBLE,
                    throttle_pct DOUBLE,
                    steering_deg DOUBLE,
                    rpm          DOUBLE,
                    lat          DOUBLE,
                    lon          DOUBLE
                );
                CREATE INDEX IF NOT EXISTS idx_telemetry_session
                    ON telemetry(session_id, frame_idx);
                CREATE TABLE IF NOT EXISTS sessions (
                    session_id    VARCHAR PRIMARY KEY,
                    driver        VARCHAR,
                    driver_level  VARCHAR,
                    track         VARCHAR,
                    car           VARCHAR,
                    started_at    TIMESTAMP DEFAULT now(),
                    ended_at      TIMESTAMP,
                    note          VARCHAR
                );
                """.trimIndent(),
            )
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
        val escPath = outFile.absolutePath.replace("'", "''")
        val escSid = sessionId.replace("'", "''")
        conn.createStatement().use { st ->
            st.execute(
                "COPY (SELECT * FROM telemetry WHERE session_id = '$escSid') " +
                    "TO '$escPath' (FORMAT PARQUET)",
            )
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
}
