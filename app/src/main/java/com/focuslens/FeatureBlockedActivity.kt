package com.focuslens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A plain, no-challenge stop for a specific blocked feature or site — Reels, Shorts, a
 * social-media domain in Chrome, or non-academic YouTube content when academic/podcast-only
 * mode is on. Unlike BlockActivity, there is nothing to pass here while the matching
 * setting is on: it is a direct rule, not an escalating challenge. Still never a true
 * lockout — one tap always returns home, and the rule itself can be turned off any time
 * from the Focus tab.
 */
class FeatureBlockedActivity : ComponentActivity() {

    companion object {
        @Volatile
        var showing = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra("title") ?: "Blocked"
        // Always say why: if no reason was given, say it is one of the person's own rules.
        val message = intent.getStringExtra("message")?.takeIf { it.isNotBlank() }
            ?: "Blocked by a rule you set in FocusLens."
        showing = true

        setContent {
            MaterialTheme(
                typography = AppTypography,
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1F3A34),
                    background = Color(0xFFF7F5F0),
                    surface = Color(0xFFF7F5F0)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BackHandler { goHome() }
                    Column(
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🚫", fontSize = 44.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        Text(message, fontSize = 15.sp, textAlign = TextAlign.Center, color = Color.Gray)
                        Spacer(Modifier.height(24.dp))
                        TextButton(onClick = { goHome() }) {
                            Text("Take me home", fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }

    private fun goHome() {
        FocusPrefs(this).bump("all", "chose_leave")
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        finish()
    }

    override fun onStop() {
        showing = false
        if (!isFinishing) finish()
        super.onStop()
    }

    override fun onDestroy() {
        showing = false
        super.onDestroy()
    }
}
