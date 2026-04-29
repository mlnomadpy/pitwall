package com.pitwall.app.data

/**
 * A single post-session coaching insight returned by GET /insights.
 *
 * Insights are ranked 1–3 (easiest gain first) and contain enough context
 * to render a rich card in InsightsTab without any further API calls.
 */
data class DriverInsight(
    val id: String,
    val rank: Int,                  // 1 = highest priority / easiest gain
    val title: String,
    val detail: String,
    val corners: List<String>,      // corner names where this issue was observed
    val metricLabel: String,        // e.g. "Coast", "Peak G"
    val metricValue: String,        // e.g. "34%", "1.42G"
    val effort: Int,                // 1 easy · 2 medium · 3 hard
    val estGainS: Float,            // estimated lap-time gain in seconds
    val evidenceBursts: Int,        // number of telemetry bursts supporting this finding
    val lap: Int = 0,               // the lap this insight was generated for
) {
    /** Severity colour bucket — drives the metric chip colour in the UI. */
    val severity: Severity get() = when (id) {
        "coast_excess"   -> if (estGainS >= 0.8f) Severity.HIGH else Severity.MEDIUM
        "grip_headroom"  -> if (estGainS >= 0.7f) Severity.HIGH else Severity.MEDIUM
        "trail_absent"   -> Severity.MEDIUM
        "braking_late"   -> Severity.MEDIUM
        else             -> Severity.LOW
    }

    enum class Severity { HIGH, MEDIUM, LOW }
}
