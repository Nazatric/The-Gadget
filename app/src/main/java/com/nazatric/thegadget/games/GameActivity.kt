package com.nazatric.thegadget.games

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.nazatric.thegadget.ui.components.GlassOrb
import com.nazatric.thegadget.ui.components.Glyph
import com.nazatric.thegadget.ui.components.GlyphKind
import com.nazatric.thegadget.ui.theme.GadgetBackdrop
import com.nazatric.thegadget.ui.theme.GadgetTheme
import com.nazatric.thegadget.ui.theme.italicLabel
import java.io.ByteArrayInputStream
import java.io.File

class GameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val root = intent.getStringExtra(EXTRA_ROOT)?.let { File(it) }
        val entry = intent.getStringExtra(EXTRA_ENTRY)?.let { File(it) }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "game"
        if (root == null || entry == null || !entry.exists()) {
            finish()
            return
        }
        setContent {
            GadgetTheme {
                GameSurface(title, root, entry) { finish() }
            }
        }
    }

    companion object {
        const val EXTRA_ROOT = "root"
        const val EXTRA_ENTRY = "entry"
        const val EXTRA_TITLE = "title"
    }
}

private fun blocked() = WebResourceResponse(
    "text/plain",
    "utf-8",
    403,
    "blocked",
    emptyMap(),
    ByteArrayInputStream(ByteArray(0)),
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GameSurface(title: String, root: File, entry: File, onBack: () -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    GadgetBackdrop(glow = 0.7f, grain = false) {
        Column(Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Row(
                Modifier.padding(top = top + 8.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                GlassOrb(44.dp, onBack) { Glyph(GlyphKind.Back, Modifier.size(18.dp)) }
                Text(title, style = italicLabel(22.sp), modifier = Modifier.padding(start = 12.dp))
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    val loader = WebViewAssetLoader.Builder()
                        .addPathHandler("/game/", WebViewAssetLoader.InternalStoragePathHandler(context, root))
                        .build()
                    WebView(context).apply {
                        setBackgroundColor(Color.BLACK)
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                return loader.shouldInterceptRequest(request.url)
                                    ?: blocked()
                            }

                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val host = request.url?.host
                                return host != "appassets.androidplatform.net"
                            }
                        }
                        val relative = entry.relativeTo(root).invariantSeparatorsPath
                        loadUrl("https://appassets.androidplatform.net/game/$relative")
                    }
                },
            )
        }
    }
}
