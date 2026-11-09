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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val END_PHRASE = "I want to end hyper focus"

/**
 * Covers every app device-wide, not just the ones individually toggled on — the whole
 * point of Hyper Focus. There's deliberately no challenge here (a math puzzle doesn't
 * fit "block literally everything"), but there's also deliberately no true lockout:
 * typing the confirmation phrase always ends it. A phone that can't be unlocked by its
 * own owner isn't a feature, regardless of how motivating that might sound.
 */
class HyperFocusBlockActivity : ComponentActivity() {

    companion object {
        @Volatile var showing = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showing = true
        val prefs = FocusPrefs(this)
        val pkg = intent.getStringExtra("pkg") ?: ""

        setContent {
            MaterialTheme(
                typography = AppTypography,
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1F3A34),
                    background = Color(0xFF14201C),
                    surface = Color(0xFF14201C)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BackHandler { goHome() }
                    HyperFocusScreen(
                        prefs = prefs,
                        onEnded = { finish() },
                        onGiveUp = { goHome() }
                    )
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

@Composable
private fun HyperFocusScreen(prefs: FocusPrefs, onEnded: () -> Unit, onGiveUp: () -> Unit) {
    var remainingMs by remember { mutableLongStateOf((prefs.hyperFocusUntil() - System.currentTimeMillis()).coerceAtLeast(0L)) }
    var confirming by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (remainingMs > 0) {
            delay(1000)
            remainingMs = (prefs.hyperFocusUntil() - System.currentTimeMillis()).coerceAtLeast(0L)
        }
        onEnded()
    }

    val indefinite = prefs.isHyperFocusIndefinite()
    val mins = remainingMs / 60_000L
    val secs = (remainingMs / 1000L) % 60L

    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🧘", fontSize = 48.sp)
        Spacer(Modifier.height(10.dp))
        Text("Hyper Focus", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(
            "Blocked because Hyper Focus is on. You turned it on, so everything except calls and " +
                "your allowed apps is paused until the timer ends. No challenge here, it's just a timer.",
            fontSize = 14.sp,
            color = Color(0xFFBFD3C9),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Text(
            if (indefinite) "No time limit" else String.format("%02d:%02d", mins, secs),
            fontSize = if (indefinite) 22.sp else 44.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(32.dp))

        if (!confirming) {
            Button(onClick = { confirming = true }) {
                Text("I need to end this early")
            }
        } else {
            Text(
                "Type: \"$END_PHRASE\"",
                fontSize = 13.sp,
                color = Color(0xFFBFD3C9),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.85f)
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    prefs.endHyperFocus()
                    onEnded()
                },
                enabled = typed.trim().equals(END_PHRASE, ignoreCase = true)
            ) {
                Text("End Hyper Focus")
            }
        }

        Spacer(Modifier.height(20.dp))
        androidx.compose.material3.TextButton(onClick = onGiveUp) {
            Text("Back to home screen", color = Color(0xFFBFD3C9))
        }
    }
}
