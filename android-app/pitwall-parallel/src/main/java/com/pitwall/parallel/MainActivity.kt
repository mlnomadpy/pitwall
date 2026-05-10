package com.pitwall.parallel

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pitwall.parallel.usb.UsbSerialProbeService

/**
 * Fullscreen WebView loading the **exact** Vue production bundle from `http://127.0.0.1:8765/`
 * (same URL + port as `python3 -m src.pitwall` / Termux — see docs).
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startUsbSerialProbeService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        webView = WebView(this).apply {
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
            }
            webChromeClient = WebChromeClient()
            post { loadUrl("http://127.0.0.1:8765/") }
        }
        setContentView(webView)

        ensureUsbSerialProbeStarted()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (::webView.isInitialized && webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            },
        )
    }

    /**
     * Phase-1 USB-serial bench probe ([UsbSerialProbeService]): foreground notification + logcat `UsbSerialProbe`.
     */
    private fun ensureUsbSerialProbeStarted() {
        if (Build.VERSION.SDK_INT >= 33) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED -> startUsbSerialProbeService()
                else -> requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startUsbSerialProbeService()
        }
    }

    private fun startUsbSerialProbeService() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, UsbSerialProbeService::class.java),
        )
    }
}
