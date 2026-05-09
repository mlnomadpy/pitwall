package com.pitwall.bridge.ktor.embedded

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.math.abs
import kotlin.math.min

/** Sonoma sector metadata for `/sector_times` (same distances as `sonoma.SECTORS`). */
internal object SonomaEmbedded {
    val SECTOR_DEFINITIONS: JsonArray = buildJsonArray {
        add(
            buildJsonObject {
                put("name", JsonPrimitive("Front Loop"))
                put("start_m", JsonPrimitive(0))
                put("end_m", JsonPrimitive(1294))
            },
        )
        add(
            buildJsonObject {
                put("name", JsonPrimitive("Carousel & Back"))
                put("start_m", JsonPrimitive(1294))
                put("end_m", JsonPrimitive(2752))
            },
        )
        add(
            buildJsonObject {
                put("name", JsonPrimitive("T10 to Calamity"))
                put("start_m", JsonPrimitive(2752))
                put("end_m", JsonPrimitive(4258))
            },
        )
    }
}

internal suspend fun EmbeddedDuckDb.persistDetectedLapsIfEmpty(
    sessionId: String,
    samples: List<EmbeddedLapEngine.TelemetrySample>,
) {
    val duck = this
    withConnection {
        if (duck.countLaps(this, sessionId) == 0 && samples.size >= 10) {
            val detected = EmbeddedLapEngine.detectLaps(samples)
            if (detected.isNotEmpty()) {
                duck.insertDetectedLaps(this, sessionId, detected)
            }
        }
    }
}

/**
 * JSON payloads for lap analytics routes using [EmbeddedLapEngine] + persisted laps.
 */
internal object EmbeddedLapPresentation {

    /** Full lap time table with sector rows and `is_best` flags; null if no laps detected. */
    fun lapTimeTable(sessionId: String, samples: List<EmbeddedLapEngine.TelemetrySample>): JsonObject? {
        val laps = EmbeddedLapEngine.detectLaps(samples)
        if (laps.isEmpty()) return null
        val bestLapTime = laps.minOf { it.lapTimeS }
        val bestLapNo = laps.first { it.lapTimeS == bestLapTime }.lapNumber

        val bestSectorTimes = mutableMapOf<String, Double>()
        for (lap in laps) {
            val slice = sliceLap(samples, lap)
            for (s in EmbeddedLapEngine.lapSectorSplits(slice)) {
                val prev = bestSectorTimes[s.name]
                if (prev == null || s.timeS < prev) bestSectorTimes[s.name] = s.timeS
            }
        }

        val lapsOut = JsonArray(
            laps.map { lap ->
                val slice = sliceLap(samples, lap)
                val splits = EmbeddedLapEngine.lapSectorSplits(slice)
                val sectorsJson = JsonArray(
                    splits.map { s ->
                        val best = bestSectorTimes[s.name] ?: s.timeS
                        buildJsonObject {
                            put("name", JsonPrimitive(s.name))
                            put("time_s", JsonPrimitive(s.timeS.round3()))
                            put("is_best", JsonPrimitive(abs(s.timeS - best) < 1e-5))
                        }
                    },
                )
                buildJsonObject {
                    put("lap_number", JsonPrimitive(lap.lapNumber))
                    put("lap_time_s", JsonPrimitive(lap.lapTimeS.round3()))
                    put("delta_to_best_s", JsonPrimitive((lap.lapTimeS - bestLapTime).round3()))
                    put("is_best", JsonPrimitive(lap.lapNumber == bestLapNo))
                    put("sectors", sectorsJson)
                }
            },
        )

        return buildJsonObject {
            put("session_id", JsonPrimitive(sessionId))
            put("lap_count", JsonPrimitive(laps.size))
            put("best_lap_s", JsonPrimitive(bestLapTime.round3()))
            put("best_lap_number", JsonPrimitive(bestLapNo))
            put("laps", lapsOut)
        }
    }

    fun sectorTimes(sessionId: String, samples: List<EmbeddedLapEngine.TelemetrySample>): JsonObject? {
        val laps = EmbeddedLapEngine.detectLaps(samples)
        if (laps.isEmpty()) return null
        val lapsOut = JsonArray(
            laps.map { lap ->
                val slice = sliceLap(samples, lap)
                val (s1, s2, s3) = EmbeddedLapEngine.sectorSplitsToS123(EmbeddedLapEngine.lapSectorSplits(slice))
                buildJsonObject {
                    put("lap_number", JsonPrimitive(lap.lapNumber))
                    put("s1", JsonPrimitive(s1.round3()))
                    put("s2", JsonPrimitive(s2.round3()))
                    put("s3", JsonPrimitive(s3.round3()))
                }
            },
        )
        return buildJsonObject {
            put("session_id", JsonPrimitive(sessionId))
            put("sector_definitions", SonomaEmbedded.SECTOR_DEFINITIONS)
            put("laps", lapsOut)
        }
    }

    fun idealLap(sessionId: String, samples: List<EmbeddedLapEngine.TelemetrySample>): JsonObject {
        val laps = EmbeddedLapEngine.detectLaps(samples)
        if (laps.isEmpty()) {
            return buildJsonObject {
                put("error", JsonPrimitive("no laps"))
                put("session_id", JsonPrimitive(sessionId))
            }
        }
        val bestBySector = mutableMapOf<String, Pair<Int, EmbeddedLapEngine.SectorSplit>>()
        for (lap in laps) {
            val slice = sliceLap(samples, lap)
            for (s in EmbeddedLapEngine.lapSectorSplits(slice)) {
                val prev = bestBySector[s.name]
                if (prev == null || s.timeS < prev.second.timeS) {
                    bestBySector[s.name] = lap.lapNumber to s
                }
            }
        }
        val missingSector = EmbeddedLapEngine.SONOMA_SECTORS.any { (name, _, _) -> bestBySector[name] == null }
        val bestActual = laps.minOf { it.lapTimeS }
        if (missingSector) {
            val sumPartial = bestBySector.values.sumOf { it.second.timeS }
            return buildJsonObject {
                put("session_id", JsonPrimitive(sessionId))
                put("ideal_lap_s", JsonPrimitive(sumPartial.round3()))
                put("best_actual_lap_s", JsonPrimitive(bestActual.round3()))
                put("gain_potential_s", JsonPrimitive((bestActual - sumPartial).round3()))
                put("best_sectors", JsonArray(emptyList()))
                put(
                    "note",
                    JsonPrimitive(
                        "embedded: incomplete sector coverage — need distance_m spanning full lap",
                    ),
                )
            }
        }
        val ideal = EmbeddedLapEngine.SONOMA_SECTORS.sumOf { (name, _, _) ->
            bestBySector[name]!!.second.timeS
        }
        val bestSectors = JsonArray(
            EmbeddedLapEngine.SONOMA_SECTORS.map { (name, _, _) ->
                val pair = bestBySector[name]!!
                buildJsonObject {
                    put("name", JsonPrimitive(pair.second.name))
                    put("time_s", JsonPrimitive(pair.second.timeS.round3()))
                    put("from_lap", JsonPrimitive(pair.first))
                }
            },
        )
        return buildJsonObject {
            put("session_id", JsonPrimitive(sessionId))
            put("ideal_lap_s", JsonPrimitive(ideal.round3()))
            put("best_actual_lap_s", JsonPrimitive(bestActual.round3()))
            put("gain_potential_s", JsonPrimitive((bestActual - ideal).round3()))
            put("best_sectors", bestSectors)
        }
    }

    private fun sliceLap(samples: List<EmbeddedLapEngine.TelemetrySample>, lap: EmbeddedLapEngine.DetectedLap): List<EmbeddedLapEngine.TelemetrySample> {
        val end = min(lap.frameEnd, samples.lastIndex).coerceAtLeast(lap.frameStart)
        return samples.slice(lap.frameStart..end)
    }
}
