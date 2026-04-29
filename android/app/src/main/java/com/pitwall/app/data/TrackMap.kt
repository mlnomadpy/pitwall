package com.pitwall.app.data

import kotlinx.serialization.Serializable

@Serializable
data class TrackMap(
    val name: String,
    val trackLength: Float,
    val sfLat: Double,
    val sfLon: Double,
    val corners: List<TrackCorner>,
    val sectors: List<TrackSector>
) {
    private fun modDistance(distance: Float): Float {
        return if (trackLength > 0f) distance % trackLength else distance
    }

    fun cornerAt(distance: Float): TrackCorner? {
        val d = modDistance(distance)
        return corners.find { d in it.startDistance..it.endDistance }
    }

    fun nearestCorner(distance: Float): TrackCorner? {
        val d = modDistance(distance)
        return corners.minByOrNull { Math.abs(it.apexDistance - d) }
    }

    fun nextCorner(distance: Float): TrackCorner? {
        val d = modDistance(distance)
        return corners.filter { it.apexDistance >= d }.minByOrNull { it.apexDistance - d }
            ?: corners.firstOrNull()
    }

    fun distanceToCorner(distance: Float, corner: TrackCorner): Float {
        val d = modDistance(distance)
        var diff = corner.apexDistance - d
        if (diff < -trackLength / 2f) {
            diff += trackLength
        }
        return diff
    }

    fun isPastApex(distance: Float, corner: TrackCorner): Boolean {
        val d = modDistance(distance)
        return d > corner.apexDistance
    }

    fun sectorAt(distance: Float): TrackSector? {
        val d = modDistance(distance)
        return sectors.find { d in it.startDistance..it.endDistance }
    }
}

@Serializable
data class TrackCorner(
    val name: String,
    val startDistance: Float,
    val apexDistance: Float,
    val endDistance: Float,
    val direction: String, // "L" or "R"
    val gear: Int,
    val elevationChange: Float,
    val entrySpeedKmh: Float,
    val apexSpeedKmh: Float,
    val exitSpeedKmh: Float,
    val camber: Float
)

@Serializable
data class TrackSector(
    val name: String,
    val startDistance: Float,
    val endDistance: Float
)

object SonomaGoldStandard {
    val track = TrackMap(
        name = "Sonoma Raceway",
        trackLength = 3765f,
        sfLat = 38.1614,
        sfLon = -122.4549,
        corners = listOf(
            TrackCorner("Turn 1", 150f, 220f, 300f, "L", 2, 0f, 111f, 113f, 117f, 0f),
            TrackCorner("Turn 3", 600f, 700f, 820f, "R", 4, 50f, 104f, 87f, 102f, 11f),
            TrackCorner("Turn 6", 1200f, 1320f, 1440f, "R", 5, 86f, 92f, 77f, 105f, -11f),
            TrackCorner("Turn 9", 2100f, 2200f, 2300f, "L", 3, 66f, 121f, 116f, 132f, -16f),
            TrackCorner("Turn 10", 2500f, 2620f, 2760f, "R", 6, 124f, 106f, 73f, 108f, 0f),
            TrackCorner("Turn 11", 2900f, 3020f, 3200f, "R", 5, 134f, 88f, 64f, 95f, 0f),
        ),
        sectors = listOf(
            TrackSector("Sector 1", 0f, 1255f),
            TrackSector("Sector 2", 1255f, 2510f),
            TrackSector("Sector 3", 2510f, 3765f),
        )
    )
}
