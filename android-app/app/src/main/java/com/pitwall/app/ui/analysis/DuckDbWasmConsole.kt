package com.pitwall.app.ui.analysis

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.pitwall.app.BuildConfig
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * In-process DuckDB-Wasm console (same CDN bundles as the Vue PWA), loaded from app assets
 * over [WebViewAssetLoader] so workers/WASM load from a secure origin.
 */
@Composable
fun DuckDbWasmConsole(
    sessionId: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val assetLoader =
        remember(context) {
            WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()
        }
    val baseEncoded =
        remember {
            URLEncoder.encode(BuildConfig.PITWALL_API_BASE_URL, StandardCharsets.UTF_8.name())
        }
    val sidEncoded =
        remember(sessionId) {
            URLEncoder.encode(sessionId.orEmpty(), StandardCharsets.UTF_8.name())
        }

    @SuppressLint("SetJavaScriptEnabled")
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient =
                    object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ) = assetLoader.shouldInterceptRequest(request.url)
                    }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                loadUrl(
                    "https://appassets.androidplatform.net/assets/www/duckdb_console.html" +
                        "?base=$baseEncoded&sid=$sidEncoded",
                )
            }
        },
        modifier = modifier,
        onRelease = { view -> view.destroy() },
    )
}
