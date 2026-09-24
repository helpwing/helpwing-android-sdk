package ru.fanyagin.helpwing.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.fanyagin.helpwing.Helpwing
import ru.fanyagin.helpwing.HelpwingProvider
import ru.fanyagin.helpwing.SupportLauncher
import ru.fanyagin.helpwing.SupportSheet
import ru.fanyagin.helpwing.core.Identity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HelpwingProvider {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    Column(
                        Modifier.safeDrawingPadding().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        BasicText("Helpwing sample", style = TextStyle(fontSize = 22.sp))
                        Action("Open as an activity") { Helpwing.open(this@MainActivity) }
                        Action("Sign in as Ada") { Helpwing.identify(Identity(id = "ada", email = "ada@example.com", name = "Ada")) }
                        Action("Sign out") { Helpwing.reset() }
                    }
                    SupportLauncher()
                    SupportSheet()
                }
            }
        }
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    BasicText(
        text = label,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 8.dp),
        style = TextStyle(color = Color(0xFF2563EB), fontSize = 16.sp),
    )
}
