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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
 * A doom-scroll nudge — not a block. Shows after a continuous streak of scrolling in a
 * tracked social app (FocusAccessibilityService counts TYPE_VIEW_SCROLLED events; see
 * doomScrollThreshold in FocusPrefs). Unlike BlockActivity/FeatureBlockedActivity, both
 * choices here are legitimate: keep scrolling is one tap away, same as take a break —
 * this is awareness, not an obstacle course.
 */
class ScrollReminderActivity : ComponentActivity() {

    companion object {
        @Volatile
        var showing = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = intent.getStringExtra("label") ?: "this app"
        val scrollCount = intent.getIntExtra("scrollCount", 0)
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
                    BackHandler { finish() }
                    Column(
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("👀", fontSize = 44.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Still scrolling?", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "That's $scrollCount scrolls in a row in $label. No judgement — just checking this is where you meant to be.",
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(28.dp))
                        Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth(0.7f)) {
                            Text("Keep scrolling", fontSize = 16.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { goHome() }, modifier = Modifier.fillMaxWidth(0.7f)) {
                            Text("Take a break", fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }

    private fun goHome() {
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
