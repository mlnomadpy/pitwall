package com.pitwall.paddock.data

import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng

/**
 * One coaching point on Sonoma. Replace with API payload when Taha’s map bundle ships.
 */
data class TrackMarker(
    val id: String,
    val title: String,
    val position: LatLng,
    /** 1 = easy … 5 = very hard */
    val difficulty: Int,
    val coaching: String,
    /** Map / detail title, e.g. "T10 LEFT BERM" */
    val shortTitle: String = title,
    /** Google Maps marker hue (0–360) [BitmapDescriptorFactory] */
    val mapHue: Float = BitmapDescriptorFactory.HUE_AZURE,
    val entrySpeedKph: Int = 142,
    val apexG: Float = 2.1f,
    /** Tooltip under map selection */
    val tooltipSubtitle: String = "High Degradation Zone",
)

fun TrackMarker.difficultyLabel(): String = when (difficulty) {
    5, 4 -> "HARD"
    3 -> "MED"
    else -> "EASY"
}

object MockSonomaData {
    val defaultCenter = LatLng(38.1611, -122.4545)

    /**
     * Polyline through markers for a simple track path (not survey-accurate).
     */
    val trackPolyline: List<LatLng> = listOf(
        LatLng(38.1630, -122.4562),
        LatLng(38.1620, -122.4560),
        LatLng(38.1603, -122.4538),
        LatLng(38.1595, -122.4555),
        LatLng(38.1608, -122.4540),
        LatLng(38.1625, -122.4550),
        LatLng(38.1630, -122.4562),
    )

    val markers: List<TrackMarker> = listOf(
        TrackMarker(
            id = "T10",
            title = "Turn 10",
            shortTitle = "T10 LEFT BERM",
            position = LatLng(38.1595, -122.4555),
            difficulty = 5,
            mapHue = BitmapDescriptorFactory.HUE_ROSE,
            coaching = "Run over left berm, use Toyota sign letters as visual guide. Commit to the inside.",
            entrySpeedKph = 142,
            apexG = 2.1f,
            tooltipSubtitle = "High Degradation Zone",
        ),
        TrackMarker(
            id = "T4",
            title = "The Carousel",
            shortTitle = "THE CAROUSEL EXIT",
            position = LatLng(38.1603, -122.4538),
            difficulty = 3,
            mapHue = BitmapDescriptorFactory.HUE_ORANGE,
            coaching = "Smooth rotation; let the car settle before throttle.",
        ),
        TrackMarker(
            id = "T7",
            title = "Turn 7",
            shortTitle = "TURN 7 APEX",
            position = LatLng(38.1620, -122.4560),
            difficulty = 3,
            mapHue = BitmapDescriptorFactory.HUE_GREEN,
            coaching = "Set car early; don’t over-slow for the late apex.",
        ),
        TrackMarker(
            id = "T11",
            title = "Calamity",
            shortTitle = "T11 APEX FOCUS",
            position = LatLng(38.1608, -122.4540),
            difficulty = 4,
            mapHue = 45f, // yellow-ish
            coaching = "Patience on entry; protect exit onto the front straight.",
        ),
    )
}
