package com.pitwall.app

import android.content.Context
import java.io.File

/**
 * Ensures the app sandbox tree for `PITWALL_ANDROID_HOME` exists and the registry
 * seed JSON is present. Chaquopy ships Python from `src/`, but we copy the static
 * seed from assets so `pitwall.db.REGISTRY_SEED_PATH` resolves under that tree.
 */
object PitwallAndroidHome {

    fun prepare(context: Context, homeAbsolutePath: String) {
        val root = File(homeAbsolutePath)
        val registryDir = File(root, "data/registry")
        registryDir.mkdirs()
        val dest = File(registryDir, "obd2_pids.json")
        if (dest.exists() && dest.length() > 0L) return
        context.assets.open("pitwall_seed/data/registry/obd2_pids.json").use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }
}
