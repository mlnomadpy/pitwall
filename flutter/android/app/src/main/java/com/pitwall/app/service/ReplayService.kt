package com.pitwall.app.service

import android.net.Uri
import android.util.Log
import com.pitwall.app.data.RawFrame
import com.pitwall.app.fusion.SensorFusion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

private const val TAG = "ReplayService"


/**
 * Replays a real Racelogic VBO file through the SensorFusion engine.
 *
 * Handles the actual VBOX hardware format:
 *   - [column names] / [data] sections (space-separated rows)
 *   - Coordinates in "raw VBox" format: divide by 100 to get decimal degrees
 *   - Timestamps in HHMMSS.SSS → converted to relative seconds from first frame
 *   - Scientific notation channel values (e.g. +4.286733E-02)
 *   - All real channel names from VBVDHD2 hardware
 */
class ReplayService(
    private val context: android.content.Context,
    private val vboUri: Uri,
    private val fusion: SensorFusion,
    private val speedMultiplier: Float = 1f,
) {
    fun start(scope: CoroutineScope) = scope.launch(Dispatchers.IO) {
        Log.i(TAG, "Replaying $vboUri at ${speedMultiplier}x")

        val frames = parseVbo(vboUri)
        if (frames.isEmpty()) {
            Log.e(TAG, "No frames parsed — check VBO format")
            return@launch
        }
        Log.i(TAG, "Parsed ${frames.size} frames")

        for ((i, frame) in frames.withIndex()) {
            fusion.onRacelogicFrame(frame)

            // Re-emit OBD channels if CAN data was present in the VBO
            if (frame.brakePressure != null || frame.rpm != null) {
                fusion.onObdFrame(frame.copy(source = "obdlink"))
            }

            // Maintain real-time playback pacing
            if (speedMultiplier > 0f && i < frames.size - 1) {
                val dtS = (frames[i + 1].timestamp - frame.timestamp).coerceIn(0.0, 5.0)
                val sleepMs = (dtS * 1000.0 / speedMultiplier).toLong().coerceIn(1, 5000)
                delay(sleepMs)
            }
        }
        Log.i(TAG, "Replay complete — ${frames.size} frames")
    }

    // ── VBO Parser ────────────────────────────────────────────────────────────

    /**
     * Column-name → canonical field mapping for the real VBOX hardware format.
     */
    private val COLUMN_MAP = mapOf(
        "sats"                              to "satellites",
        "time"                              to "timestamp",
        "lat"                               to "lat",
        "long"                              to "lon",
        "velocity"                          to "speedKmh",
        "heading"                           to "heading",
        "comboAcc"                          to "comboG",
        "Indicated_Lateral_Acceleration"    to "gLat",
        "Indicated_Longitudinal_Acceleration" to "gLong",
        "Indicated_Vehicle_Speed"           to "speedKmhObd",   // backup speed
        "Brake_Pressure"                    to "brakePressure",
        "Brake_Position"                    to "brakePosition",
        "Throttle_Position"                 to "throttle",
        "Engine_Speed"                      to "rpm",
        "Steering_Angle"                    to "steering",
        "Coolant_Temperature"               to "coolantTemp",
        "Oil_Temperature"                   to "oilTemp",
        // Legacy / synthetic VBO field names also supported
        "latitude"                          to "lat",
        "longitude"                         to "lon",
        "velocity kmh"                      to "speedKmh",
        "lateral-g"                         to "gLat",
        "longitudinal-g"                    to "gLong",
        "combined-g"                        to "comboG",
        "brake pressure"                    to "brakePressure",
        "brake pos"                         to "brakePosition",
        "throttle pos"                      to "throttle",
        "engine rpm"                        to "rpm",
        "steering angle"                    to "steering",
        "coolant temp"                      to "coolantTemp",
        "oil temp"                          to "oilTemp",
        "distance"                          to "distance",
    )

    private fun parseVbo(uri: Uri): List<RawFrame> {
        val frames = mutableListOf<RawFrame>()
        var inData = false
        var inColumnNames = false
        val colIndex = mutableMapOf<String, Int>()  // canonical name → column index
        var startTimeSecs: Double? = null

        val inputStream = context.contentResolver.openInputStream(uri)
            ?: run { Log.e(TAG, "Cannot open URI: $uri"); return emptyList() }

        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            reader.forEachLine { raw ->
            val line = raw.trim()
            when {
                // Section markers
                line.equals("[data]", ignoreCase = true) -> {
                    inData = true; inColumnNames = false
                    return@forEachLine
                }
                line.equals("[column names]", ignoreCase = true) -> {
                    inColumnNames = true; inData = false
                    return@forEachLine
                }
                line.startsWith("[") -> {
                    inData = false; inColumnNames = false
                    return@forEachLine
                }

                // Column names line — the single space-separated line after [column names]
                inColumnNames && colIndex.isEmpty() && line.isNotBlank() -> {
                    line.split(Regex("\\s+")).forEachIndexed { idx, name ->
                        val canonical = COLUMN_MAP[name]
                            ?: COLUMN_MAP[name.lowercase()]
                            ?: COLUMN_MAP[name.replace("_", " ").lowercase()]
                        if (canonical != null) colIndex[canonical] = idx
                    }
                    inColumnNames = false   // consumed
                    Log.d(TAG, "Mapped ${colIndex.size} columns: $colIndex")
                }

                // Data rows
                inData && line.isNotBlank() -> {
                    val parts = line.split(Regex("\\s+"))
                    fun col(name: String): String? =
                        colIndex[name]?.let { parts.getOrNull(it)?.trim() }

                    // ── Timestamp ────────────────────────────────────────────
                    // Format: HHMMSS.SSS → convert to seconds-of-day, then relative
                    val rawTime = col("timestamp")?.toDoubleOrNull() ?: return@forEachLine
                    val h = (rawTime / 10000).toInt()
                    val m = ((rawTime / 100) % 100).toInt()
                    val s = rawTime % 100
                    val todSecs = h * 3600.0 + m * 60.0 + s
                    val startTs = startTimeSecs ?: todSecs.also { startTimeSecs = it }
                    val relSecs = todSecs - startTs

                    // ── GPS coordinates ──────────────────────────────────────
                    // VBOX stores raw coordinates ÷ 100 = decimal degrees
                    val lat = col("lat")?.toDoubleOrNull()?.let { it / 100.0 }
                    val lon = col("lon")?.toDoubleOrNull()?.let { it / 100.0 }

                    // ── Speed ────────────────────────────────────────────────
                    // Prefer GPS velocity; fall back to OBD indicated speed
                    val speedKmh = col("speedKmh")?.toFloatOrNull()
                        ?: col("speedKmhObd")?.toFloatOrNull()
                        ?: 0f
                    val speedMs = speedKmh / 3.6f

                    // ── G-forces ──────────────────────────────────────────────
                    val gLat   = col("gLat")?.toFloatOrNull()
                    val gLong  = col("gLong")?.toFloatOrNull()
                    val comboG = col("comboG")?.toFloatOrNull()
                        ?: if (gLat != null && gLong != null)
                            Math.sqrt((gLat * gLat + gLong * gLong).toDouble()).toFloat()
                        else null

                    frames += RawFrame(
                        timestamp     = relSecs,
                        source        = "racelogic",
                        lat           = lat,
                        lon           = lon,
                        speedMs       = speedMs,
                        heading       = col("heading")?.toFloatOrNull(),
                        gLat          = gLat,
                        gLong         = gLong,
                        comboG        = comboG,
                        distance      = col("distance")?.toFloatOrNull(),
                        satellites    = col("satellites")?.trim()?.toIntOrNull(),
                        brakePressure = col("brakePressure")?.toFloatOrNull(),
                        brakePosition = col("brakePosition")?.toFloatOrNull(),
                        throttle      = col("throttle")?.toFloatOrNull(),
                        rpm           = col("rpm")?.toFloatOrNull(),
                        steering      = col("steering")?.toFloatOrNull(),
                        coolantTemp   = col("coolantTemp")?.toFloatOrNull(),
                        oilTemp       = col("oilTemp")?.toFloatOrNull(),
                    )
                }
            }
            }  // end forEachLine
        }  // end BufferedReader.use

        return frames
    }
}
