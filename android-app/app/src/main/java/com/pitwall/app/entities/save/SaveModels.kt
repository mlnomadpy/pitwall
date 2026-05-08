package com.pitwall.app.entities.save

import kotlinx.serialization.Serializable

/** Mirrors [src/pwa/src/shared/types/save.ts] — subset persisted on device. */
@Serializable
data class SaveSlot(
    val schemaVersion: Int = 1,
    val id: Int,
    val createdAt: String,
    val lastPlayedAt: String,
    val driverName: String,
    val driverAvatar: String = "avatar_a",
    val skillLevel: String = "beginner",
    val car: String = "BMW M3 (E46)",
    val avatarSlot: Int = 1,
    val preferredCoach: String = "trod",
    val preferredTrack: String = "sonoma",
    val level: Int = 1,
    val sessions: List<SessionSummarySlot> = emptyList(),
    val bestLapBySession: Map<String, Double> = emptyMap(),
    val coachAffinity: Map<String, Int> =
        mapOf(
            "trod" to 1,
            "bentley" to 0,
            "drill" to 0,
            "calm" to 0,
            "buddy" to 0,
        ),
    val unlockedTracks: List<String> = listOf("sonoma"),
    val unlockedAvatars: List<Int> = listOf(1),
    val unlockedCoaches: List<String> = listOf("trod", "buddy"),
    val medals: List<String> = emptyList(),
    val goalsHistory: List<String> = emptyList(),
    val settings: SaveSettingsSlot = SaveSettingsSlot(),
)

@Serializable
data class SessionSummarySlot(
    val sessionId: String,
    val startedAt: String = "",
    val trackId: String = "sonoma",
    val bestLapS: Double? = null,
    val lapCount: Int = 0,
    val coachId: String = "trod",
)

@Serializable
data class SaveSettingsSlot(
    val masterVolume: Int = 80,
    val musicVolume: Int = 50,
    val sfxVolume: Int = 100,
    val voiceVolume: Int = 100,
    val coachMute: Boolean = false,
    val nightMode: Boolean = false,
    val reducedMotion: Boolean = false,
    val showFps: Boolean = false,
    val keyboardLayout: String = "arrows",
    val swapAB: Boolean = false,
)
