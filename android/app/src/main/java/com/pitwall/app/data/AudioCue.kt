package com.pitwall.app.data

enum class Pattern {
    SILENT, CONTINUOUS, PULSE, FAST_PULSE, SHARP, BUZZ,
    CHIME_UP, CHIME_DOWN, CHIME_NEUTRAL
}

data class AudioCue(
    val layer: String,
    val frequency: Float,
    val volume: Float,
    val pattern: Pattern,
    val priority: Int,
    val reason: String,
)

data class CoachingMessage(
    val text: String,
    val priority: Int,          // 1=strategy, 2=technique, 3=safety
    val source: Source,
    val targetCorner: String?,
    val createdAt: Long = System.currentTimeMillis(),
) {
    enum class Source { HOT_PATH, WARM_PATH }

    val ageMs get() = System.currentTimeMillis() - createdAt
    val isStale get() = ageMs > STALE_THRESHOLD_MS

    fun toChannelMap(): Map<String, Any?> = mapOf(
        "text" to text,
        "priority" to priority,
        "source" to source.name,
        "targetCorner" to targetCorner,
    )

    companion object {
        const val STALE_THRESHOLD_MS = 5_000L
        const val COOLDOWN_MS = 3_000L
    }
}
