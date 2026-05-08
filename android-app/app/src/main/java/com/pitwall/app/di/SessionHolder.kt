package com.pitwall.app.di

/**
 * In-memory active session for HUD, analytics, and coach calls.
 * [com.pitwall.app.data.local.AppPreferences] persists driver id into SessionHolder on startup.
 */
object SessionHolder {
    @Volatile
    var activeSessionId: String? = null

    @Volatile
    var activeDriver: String = "driver"

    fun clear() {
        activeSessionId = null
    }
}
