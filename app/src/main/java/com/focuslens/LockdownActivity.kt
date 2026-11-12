package com.focuslens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The lockdown screen. Shown over any app that is not allowed during a lockdown. The only ways out are
 * an allowed app, leaving for home, or the quiz. Passing the quiz opens the phone for a few minutes.
 */
class LockdownActivity : ComponentActivity() {
    companion object {
        @Volatile var showing = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showing = true
        val reason = intent.getStringExtra("reason").orEmpty()
        setBrandedContent {
            MaterialTheme { LockdownScreen(reason) { finish() } }
        }
    }

    override fun onDestroy() {
        showing = false
        super.onDestroy()
    }
}

@Composable
private fun LockdownScreen(reason: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val allowed = remember { LockdownPrefs.allowed(context) }
    val launchable = remember { loadLaunchableApps(context).filter { it.pkg in allowed }.take(6) }
    var unlocking by remember { mutableStateOf(false) }
    val quiz = remember { QuizPrefs(context).gateConfig() }

    if (unlocking && quiz != null) {
        QuizGate(
            config = quiz,
            accent = Color(0xFF1F3A34),
            onSuccess = {
                LockdownPrefs.setUnlockUntil(context, System.currentTimeMillis() + 5 * 60_000L)
                onClose()
            },
            onHome = { unlocking = false }
        )
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Phone limit reached", fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(reason, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(
            "Only the apps you allowed are open for now: alarm, music and WhatsApp by default. " +
                "Settings, the dialler and FocusLens stay open so you are never stuck.",
            fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        launchable.forEach { app ->
            Button(onClick = {
                context.packageManager.getLaunchIntentForPackage(app.pkg)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
                onClose()
            }, modifier = Modifier.fillMaxWidth()) { Text("Open ${app.label}") }
        }
        if (launchable.isEmpty()) {
            Text("No allowed apps are installed. Change them in FocusLens → Lockdown.", fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(12.dp))
        if (quiz != null) {
            OutlinedButton(onClick = { unlocking = true }, modifier = Modifier.fillMaxWidth()) { Text("Unlock for 5 minutes (quiz)") }
        } else {
            Text("Unlocking needs the quiz, which is not set up. The lockdown ends at the next daily reset.", fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Go to home screen") }
    }
}
