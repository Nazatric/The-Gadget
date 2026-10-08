package com.nazatric.thegadget

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import com.nazatric.thegadget.ui.GadgetRoot
import com.nazatric.thegadget.ui.theme.GadgetTheme

class MainActivity : ComponentActivity() {
    private val openNow = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as GadgetApp
        app.container.player.connect()
        if (intent?.getStringExtra(EXTRA_OPEN) == "now") openNow.value = true
        setContent {
            GadgetTheme {
                GadgetRoot(app.container, openNow.value) { openNow.value = false }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getStringExtra(EXTRA_OPEN) == "now") openNow.value = true
    }

    companion object {
        const val EXTRA_OPEN = "open"
    }
}
