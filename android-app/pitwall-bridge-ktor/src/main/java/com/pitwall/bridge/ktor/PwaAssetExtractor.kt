package com.pitwall.bridge.ktor

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.IOException

/**
 * Copies [assets/pwa-www] (Vue `vite build` output) into app files dir so Ktor can serve files with correct MIME types / SPA fallback.
 */
object PwaAssetExtractor {

    private const val ASSET_ROOT = "pwa-www"
    private const val PREFS = "pitwall_pwa_assets"
    private const val KEY_VERSION = "copied_version"

    fun ensureInstalled(context: Context): File {
        val dest = File(context.filesDir, ASSET_ROOT)
        val pm = context.packageManager.getPackageInfo(context.packageName, 0)
        val vc = if (android.os.Build.VERSION.SDK_INT >= 28) {
            pm.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            pm.versionCode.toString()
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_VERSION, null) == vc && File(dest, "index.html").isFile) {
            return dest
        }
        dest.deleteRecursively()
        dest.mkdirs()
        copyAssetDir(context.assets, ASSET_ROOT, dest)
        prefs.edit().putString(KEY_VERSION, vc).apply()
        return dest
    }

    /**
     * [dest] is the output path: either a directory or a file path for a leaf asset.
     */
    private fun copyAssetDir(am: AssetManager, assetPath: String, dest: File) {
        val items = am.list(assetPath)
        if (items.isNullOrEmpty()) {
            dest.parentFile?.mkdirs()
            am.open(assetPath).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        dest.mkdirs()
        for (name in items) {
            val srcPath = "$assetPath/$name"
            copyAssetDir(am, srcPath, File(dest, name))
        }
    }
}
