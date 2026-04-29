package com.pitwall.paddock.ui.briefing

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pitwall.paddock.ui.theme.PitwallBg
import com.pitwall.paddock.ui.theme.PitwallCyan
import com.pitwall.paddock.ui.theme.TextPrimary
import com.pitwall.paddock.ui.theme.TextSecondary
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding

@Composable
fun WebBriefScreen(
    webBriefBaseUrl: String,
    focusMarkerIds: Set<String>,
    onBack: () -> Unit,
) {
    val focusParam = focusMarkerIds.joinToString(",")
    val loadUrl = remember(webBriefBaseUrl, focusParam) {
        if (webBriefBaseUrl.isNotBlank()) {
            val sep = if ("?" in webBriefBaseUrl) "&" else "?"
            if (focusParam.isNotEmpty()) {
                webBriefBaseUrl.trimEnd('/') + "${sep}focus=$focusParam&track=sonoma"
            } else {
                webBriefBaseUrl.trimEnd('/') + "${sep}track=sonoma"
            }
        } else {
            null
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(PitwallBg)
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Text(
                "Pre-brief · track navigation",
                color = PitwallCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            if (loadUrl == null) {
                "Set WEB_BRIEF_BASE_URL in local.properties to load your Three.js / hosted briefing."
            } else {
                "Focus: ${focusParam.ifEmpty { "none" }} (passed as ?focus=)"
            },
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        )
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    if (loadUrl != null) {
                        loadUrl(loadUrl)
                    } else {
                        loadDataWithBaseURL(
                            "https://pitwall.local/brief",
                            buildEmbeddedBriefHtml(focusParam),
                            "text/html",
                            "UTF-8",
                            null,
                        )
                    }
                }
            },
            update = { w ->
                if (loadUrl != null) w.loadUrl(loadUrl)
            },
        )
    }
}

private fun buildEmbeddedBriefHtml(focus: String): String {
    val f = if (focus.isEmpty()) "—" else focus.replace(",", ", ")
    return """
        <!DOCTYPE html><html><head><meta name="viewport" content="width=device-width" />
        <style>
          body { background:#121212; color:#e0e0e0; font-family:system-ui,sans-serif; margin:0; padding:16px; }
          h1 { color:#00e5ff; font-size:16px; }
          p { line-height:1.45; font-size:13px; }
          .box { border:1px solid #2a2a2a; border-radius:8px; padding:12px; margin-top:12px; }
        </style></head><body>
          <h1>Sonoma · pre-brief (embedded stub)</h1>
          <p>Focus corners: <strong>$f</strong></p>
          <div class="box">Host your Three.js or MapLibre build elsewhere and set <code>WEB_BRIEF_BASE_URL</code> in
          <code>android-app/local.properties</code>. This WebView will open that URL and append <code>focus</code> + <code>track=sonoma</code>.</div>
        </body></html>
    """.trimIndent()
}
