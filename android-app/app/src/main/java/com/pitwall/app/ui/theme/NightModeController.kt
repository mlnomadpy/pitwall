package com.pitwall.app.ui.theme

import androidx.appcompat.app.AppCompatDelegate

/**
 * Syncs save-slot **Display › Night mode** with the hosting [android.app.Activity] UI mode so system
 * chrome (status bar, nav bar, splash configuration) matches [PitwallTheme] — in addition to the
 * in-app Material 3 color scheme driven by [SaveStore].
 */
object NightModeController {

    fun applyNightModeEnabled(enabled: Boolean) {
        AppCompatDelegate.setDefaultNightMode(
            if (enabled) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            },
        )
    }
}
