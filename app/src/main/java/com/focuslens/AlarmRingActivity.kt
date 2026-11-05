package com.focuslens

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
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
import com.focuslens.data.AlarmEntity
import com.focuslens.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The real, survives-closing-the-app alarm screen — shown over the lock screen with the screen
 * forced on, a looping alarm tone at full alarm-stream volume, and repeating vibration, until
 * Snoozed or Dismissed. This is the one thing the web version genuinely cannot do.
 */
class AlarmRingActivity : ComponentActivity() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val label = intent.getStringExtra("label") ?: "Alarm"
        val time = intent.getStringExtra("time") ?: ""

        startSound()
        startVibration()

        setContent {
            MaterialTheme(
                typography = AppTypography,
                colorScheme = lightColorScheme(primary = Color(0xFFB3261E), background = Color(0xFFB3261E))
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFB3261E)) {
                    BackHandler { /* swallow back — must Dismiss or Snooze explicitly */ }
                    AlarmRingScreen(
                        label = label,
                        time = time,
                        onDismiss = { stopAlarm(); finish() },
                        onSnooze = { stopAlarm(); snooze(label); finish() }
                    )
                }
            }
        }
    }

    private fun startSound() {
        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmRingActivity, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            // No alarm sound available on this device/emulator — vibration still runs.
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 800, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopAlarm() {
        player?.let { try { it.stop(); it.release() } catch (e: Exception) {} }
        player = null
        vibrator?.cancel()
    }

    private fun snooze(label: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.get(this@AlarmRingActivity).alarmDao()
            val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MINUTE, 5) }
            val snoozed = AlarmEntity(
                hour = cal.get(java.util.Calendar.HOUR_OF_DAY),
                minute = cal.get(java.util.Calendar.MINUTE),
                label = "$label (snoozed)",
                days = "",
                enabled = true
            )
            val id = dao.insert(snoozed)
            AlarmScheduler.schedule(this@AlarmRingActivity, snoozed.copy(id = id))
        }
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
}

@Composable
fun AlarmRingScreen(label: String, time: String, onDismiss: () -> Unit, onSnooze: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("⏰", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text(label, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(time, fontSize = 17.sp, color = Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(32.dp))
        Row {
            OutlinedButton(
                onClick = onSnooze,
                border = BorderStroke(1.dp, Color.White)
            ) { Text("Snooze 5 min", color = Color.White) }
            Spacer(Modifier.width(14.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB3261E))
            ) { Text("Dismiss", fontWeight = FontWeight.Bold) }
        }
    }
}
