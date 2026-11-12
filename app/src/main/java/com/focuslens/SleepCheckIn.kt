package com.focuslens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * When you come back to the phone after an estimated night, asks: "I sensed you fell asleep at X and
 * used your phone again at Y. Shall I mark that as sleep?" No leads to: "Shall I consider it as you on
 * a break?" Nothing is written until you answer.
 */
@Composable
fun SleepCheckIn() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<SleepNight?>(null) }
    // 0 = ask sleep, 1 = ask break, 2 = adjust the times
    var step by remember { mutableIntStateOf(0) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(tick) {
        if (!SleepStore.enabled(context) || !UsageRepository(context).hasPermission()) {
            pending = null
            return@LaunchedEffect
        }
        val found = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            SleepStore.refresh(context, now)
            SleepStore.pendingCheck(context, now)
        }
        pending = found
        step = 0
    }

    val p = pending ?: return
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val fmtDay = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    val when_ = "${fmtDay.format(Date(p.startMs))} ${fmt.format(Date(p.startMs))} to ${fmt.format(Date(p.endMs))}"

    when (step) {
        0 -> AlertDialog(
            onDismissRequest = { step = 1 },
            title = { Text("Did you sleep?") },
            text = {
                Text(
                    "I sensed you fell asleep at ${fmt.format(Date(p.startMs))} and used your phone again at " +
                        "${fmt.format(Date(p.endMs))}. Shall I mark that as sleep?\n\n($when_)"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) { SleepStore.answer(context, p, "confirmed") }
                    pending = null
                }) { Text("Yes, mark as sleep") }
            },
            dismissButton = {
                androidx.compose.foundation.layout.Row {
                    TextButton(onClick = { step = 2 }) { Text("Adjust times") }
                    TextButton(onClick = { step = 1 }) { Text("No") }
                }
            }
        )
        1 -> AlertDialog(
            onDismissRequest = {
                scope.launch(Dispatchers.IO) { SleepStore.answer(context, p, "dismissed") }
                pending = null
            },
            title = { Text("Was it a break?") },
            text = { Text("Shall I consider it as you being on a break, not sleep?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) { SleepStore.answer(context, p, "break") }
                    pending = null
                }) { Text("Yes, a break") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) { SleepStore.answer(context, p, "dismissed") }
                    pending = null
                }) { Text("No, leave it") }
            }
        )
        else -> {
            var built by remember { mutableStateOf<SleepNight?>(null) }
            NightDialog(
                title = "Adjust the times",
                initialBed = fmt.format(Date(p.startMs)),
                initialWake = fmt.format(Date(p.endMs)),
                onCancel = { step = 0 },
                onSave = { bed, wake ->
                    val n = nightFromInputs(p.endMs, bed, wake)
                    scope.launch(Dispatchers.IO) {
                        SleepStore.answer(context, p, "confirmed", n.startMs, n.endMs)
                    }
                    pending = null
                },
                onDelete = null
            )
        }
    }
}
