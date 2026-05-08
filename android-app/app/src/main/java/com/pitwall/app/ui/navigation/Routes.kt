package com.pitwall.app.ui.navigation

/**
 * Navigation destinations aligned with [src/pwa/src/app/router/index.ts].
 * Values are stable route IDs for Jetpack Navigation (no leading slash).
 */
object Routes {
    const val TITLE = "title"
    const val SAVE = "save"
    /** Flask sessions list (distinct from PWA save slots). */
    const val BRIDGE_SESSIONS = "bridge/sessions"
    /** Session detail — path param [sid]. */
    const val SESSION_DETAIL = "session/{sid}"
    const val ONBOARDING = "onboarding/{step}"
    const val GARAGE = "garage"
    const val CAR_SETUP = "garage/setup"
    const val TRAINER_CARD = "garage/trainer"
    const val COACH_SELECT = "garage/coach"
    const val COACH_BIOS = "garage/coach/bios"
    const val PIT_STALL = "garage/pit-stall"
    const val HARDWARE = "garage/pit-stall/hardware"
    const val PIT_STALL_LIVE = "pit-stall/live"
    const val QUESTS = "garage/quests"
    const val SPONSORS = "garage/sponsors"
    const val ANALYSIS = "garage/analysis"
    /** GET /insights — burst-derived coaching gaps */
    const val INSIGHTS = "analysis/insights"
    /** GET /session/{sid}/lap_time_distribution */
    const val LAP_DISTRIBUTION = "analysis/lap-distribution"
    const val LAP_TIMES = "analysis/lap-times"
    const val COMPARE = "analysis/compare"
    const val CORNERS = "analysis/corners"
    const val STRAIGHTS = "analysis/straights"
    const val TRACK_WALK = "analysis/track"
    /** GET /track/markers, /danger_zones, /weather, /{id}/elevation */
    const val TRACK_REFERENCE = "analysis/track-reference"
    const val ATLAS = "analysis/atlas"
    const val EVOLUTION = "analysis/evolution"
    /** Debrief bundle sections (after POST /coach/debrief) */
    const val ANALYSIS_BUNDLE = "analysis/bundle"
    const val SECTOR_TIMES = "analysis/sector-times"
    const val SESSION_CLIPS = "analysis/clips"
    const val BRAKE_ACCEL = "analysis/brake-acceleration"
    const val THROTTLE_CORNER_BOX = "analysis/throttle-corner-box"
    const val CORNER_CLASSIFICATION = "analysis/corner-classification"
    const val PEDALS = "analysis/pedals"
    const val GHOSTS = "analysis/ghosts"
    const val REPLAY = "analysis/replay"
    const val SQL = "analysis/sql"
    /** Paddock Q&A — POST /coach/ask */
    const val COACH_ASK = "coach/ask"
    /** Bentley concepts catalog — GET /coach/concepts */
    const val COACH_CONCEPTS = "coach/concepts"
    const val BRIEFING = "briefing"
    const val HUD = "hud"
    const val STAGE_CLEAR = "stage-clear"
    const val CALIBRATION = "calibration"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"
    const val END_OF_DAY = "end-of-day"
    const val LEADERBOARD = "leaderboard"
}
