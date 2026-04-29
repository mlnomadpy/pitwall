package com.pitwall.app.data

/**
 * Running accumulator for a single corner — updated every frame the driver is in that corner.
 * Internal to PitwallService; converted to [CornerSessionStats] for ViewModel consumption.
 */
internal data class CornerAccumulator(
    val name: String,
    var sumEntryKmh: Float = 0f,
    var sumApexKmh: Float  = 0f,
    var sumExitKmh: Float  = 0f,
    var entryFrames: Int   = 0,
    var apexFrames: Int    = 0,
    var exitFrames: Int    = 0,
    var maxG: Float        = 0f,
    var coastFrames: Int   = 0,
    var trailFrames: Int   = 0,
    var totalFrames: Int   = 0,
) {
    fun toStats(corner: TrackCorner): CornerSessionStats {
        return CornerSessionStats(
            name             = name,
            observedEntryKmh = if (entryFrames > 0) sumEntryKmh / entryFrames else 0f,
            observedApexKmh  = if (apexFrames > 0) sumApexKmh / apexFrames else 0f,
            observedExitKmh  = if (exitFrames > 0) sumExitKmh / exitFrames else 0f,
            refEntryKmh      = corner.entrySpeedKmh,
            refApexKmh       = corner.apexSpeedKmh,
            refExitKmh       = corner.exitSpeedKmh,
            peakG            = maxG,
            coastPct         = if (totalFrames > 0) coastFrames * 100f / totalFrames else 0f,
            trailPct         = if (entryFrames > 0) trailFrames * 100f / entryFrames else 0f,
            sampleCount      = totalFrames,
        )
    }
}

/**
 * Per-corner session statistics compared against the Sonoma gold standard.
 * Exposed from PitwallService → ViewModel → CornersTab.
 */
data class CornerSessionStats(
    val name: String,
    val observedEntryKmh: Float,   // driver's session average at entry
    val observedApexKmh: Float,
    val observedExitKmh: Float,
    val refEntryKmh: Float,        // gold-standard from TrackCorner
    val refApexKmh: Float,
    val refExitKmh: Float,
    val peakG: Float,
    val coastPct: Float,
    val trailPct: Float,
    val sampleCount: Int,
) {
    val entryDeltaKmh get() = observedEntryKmh - refEntryKmh
    val apexDeltaKmh  get() = observedApexKmh  - refApexKmh
    val exitDeltaKmh  get() = observedExitKmh  - refExitKmh

    /** Combined loss relative to reference — used for "sort by biggest loss". */
    val totalDeltaKmh get() = entryDeltaKmh + apexDeltaKmh + exitDeltaKmh

    val hasData get() = sampleCount > 0
}
