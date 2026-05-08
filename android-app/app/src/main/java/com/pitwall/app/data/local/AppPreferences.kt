package com.pitwall.app.data.local

import android.content.Context
import com.pitwall.app.di.SessionHolder

/**
 * Minimal persisted prefs — mirrors concepts from the PWA save slot until a full DataStore layer exists.
 */
object AppPreferences {

    private const val PREFS_NAME = "pitwall_prefs"
    private const val KEY_DRIVER_ID = "driver_id"

    fun hydrateSessionHolder(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = p.getString(KEY_DRIVER_ID, null)?.trim().orEmpty()
        if (stored.isNotEmpty()) {
            SessionHolder.activeDriver = stored
        }
    }

    fun saveDriverId(context: Context, driverId: String) {
        val trimmed = driverId.trim().ifEmpty { "driver" }
        SessionHolder.activeDriver = trimmed
        if (SaveStore.activeSlot() != null) {
            SaveStore.persistActiveDriverName(context, trimmed)
        } else {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_DRIVER_ID, trimmed)
                .apply()
        }
    }

    fun readDriverId(context: Context): String {
        SaveStore.activeSlot()?.driverName?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return it
        }
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DRIVER_ID, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: SessionHolder.activeDriver
    }
}
