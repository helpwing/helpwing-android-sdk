package ru.fanyagin.helpwing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier

/** The chat as a screen of its own, for apps without Compose. Start it with [Helpwing.open]. */
class SupportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (!Helpwing.isInitialized) {
            finish()
            return
        }
        setContent {
            HelpwingProvider {
                val theme = rememberSupport().theme
                Box(Modifier.fillMaxSize().background(theme.background).safeDrawingPadding()) {
                    SupportScreen(onClose = { finish() })
                }
            }
        }
    }
}
