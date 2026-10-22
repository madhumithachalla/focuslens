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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

/**
 * Shown when a blocked app is opened during an active block schedule. Offers a way out
 * (home), and, for non-strict schedules, an emergency pass that opens the app for 10 minutes
 * once the configured delay has counted down.
 */
class SchedulePauseActivity : ComponentActivity() {

    companion object {
        @Volatile var showing = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showing = true
        val pkg = intent.getStringExtra("pkg") ?: ""
        val scheduleName = intent.getStringExtra("name") ?: ""
        val endMinutes = intent.getIntExtra("end", 0)
        val strict = intent.getBooleanExtra("strict", false)
        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
        val schedulePrefs = BlockSchedulePrefs(this)
        val focusPrefs = FocusPrefs(this)
        // Quiz gate before the emergency pass (never for strict schedules, which allow no bypass).
        // Each time the gate is shown counts as one attempt today (counted when it opens).
        val quizPrefs = QuizPrefs(this)
        val quizGate = if (strict) null else quizPrefs.gateConfig()

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1F3A34),
                    background = Color(0xFF14201C),
                    surface = Color(0xFF14201C)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BackHandler { goHome() }
                    SchedulePauseScreen(
                        appName = appName,
                        scheduleName = scheduleName,
                        endLabel = formatMinutes(endMinutes),
                        strict = strict,
                        delaySeconds = schedulePrefs.delaySeconds(),
                        passesLeft = schedulePrefs.passesLeft(),
                        quizGate = quizGate,
                        onQuizOpened = { quizPrefs.addAttempt() },
                        onHome = { goHome() },
                        onPass = {
                            if (schedulePrefs.usePass()) {
                                focusPrefs.grant(pkg, 10)
                                finish()
                            }
                        }
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
private fun SchedulePauseScreen(
    appName: String,
    scheduleName: String,
    endLabel: String,
    strict: Boolean,
    delaySeconds: Int,
    passesLeft: Int,
    onHome: () -> Unit,
    onPass: () -> Unit,
    quizGate: QuizGateConfig? = null,
    onQuizOpened: () -> Unit = {}
) {
    var remaining by remember { mutableIntStateOf(delaySeconds) }
    var quizOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining -= 1
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Ouch!", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(10.dp))
        Text(
            "You have blocked $appName until $endLabel",
            fontSize = 18.sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        if (scheduleName.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(scheduleName, fontSize = 14.sp, color = Color(0xFFBFD3C9), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(28.dp))
        Button(onClick = onHome) {
            Text("TAKE ME OUT OF HERE")
        }

        if (!strict) {
            Spacer(Modifier.height(24.dp))
            when {
                passesLeft <= 0 -> Text(
                    "No emergency passes left today",
                    fontSize = 13.sp,
                    color = Color(0xFFBFD3C9)
                )
                remaining > 0 -> Text(
                    "Emergency pass available in ${remaining}s",
                    fontSize = 13.sp,
                    color = Color(0xFFBFD3C9)
                )
                quizGate != null && quizOpen -> QuizGate(
                    // The countdown above already ran, so the gate itself starts straight away.
                    config = quizGate.copy(delaySeconds = 0),
                    accent = Color.White,
                    onSuccess = onPass,
                    onHome = onHome
                )
                quizGate != null -> OutlinedButton(onClick = {
                    quizOpen = true
                    onQuizOpened()
                }) {
                    Text("Emergency pass ($passesLeft left): answer to unlock", color = Color(0xFFBFD3C9))
                }
                else -> OutlinedButton(onClick = onPass) {
                    Text("Emergency pass ($passesLeft left) - 10 min", color = Color(0xFFBFD3C9))
                }
            }
        }
    }
}
