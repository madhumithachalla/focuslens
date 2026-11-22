package com.focuslens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 20 hours away from the phone, inspired by 20 Hours for 20Talk. Not an official 20Talk feature.
 * Everything except the apps you keep open pauses for 20 hours. You can end it early, with no penalty.
 */
@Composable
fun TwentyHoursCard() {
    val context = LocalContext.current
    var until by remember { mutableLongStateOf(LockdownPrefs.challengeUntil(context)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(30_000L) }
    }
    val active = until > now

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("20 hours away", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (active) "${Clock.untilHms(until, now)} left" else "Only the apps you keep open work for 20 hours.",
                fontSize = 13.sp,
                color = Color.Gray
            )
            Spacer(Modifier.height(10.dp))
            if (active) {
                OutlinedButton(onClick = {
                    LockdownPrefs.setChallengeUntil(context, 0L)
                    until = 0L
                }) { Text("End early") }
            } else {
                Button(onClick = { confirm = true }) { Text("Start") }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Start 20 hours?") },
            text = { Text("Everything pauses except the apps you keep open. You can end early at any time, with no penalty.") },
            confirmButton = {
                TextButton(onClick = {
                    val end = System.currentTimeMillis() + 20L * 60 * 60 * 1000
                    LockdownPrefs.setChallengeUntil(context, end)
                    until = end
                    confirm = false
                }) { Text("Start") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}
