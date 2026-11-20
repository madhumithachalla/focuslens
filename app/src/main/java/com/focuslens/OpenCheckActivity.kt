package com.focuslens

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Shown each time Instagram is opened fresh. It is either a breathing exercise or a maths
 * question, and the kind changes each time you open the app. Finishing it lets you in.
 * "Not now" sends you home and is counted.
 */
class OpenCheckActivity : ComponentActivity() {

    companion object {
        @Volatile var showing = false
    }

    private val pkg by lazy { intent.getStringExtra("pkg") ?: SectionDetector.INSTAGRAM }
    private val mode by lazy { FocusPrefs(this).nextInterceptMode() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showing = true
        window.setBackgroundDrawable(ColorDrawable(0xFF0E0F13.toInt()))
        // Back counts as "Not now", so it can't be used to slip past the check.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = notNow()
        })
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFA9A6F7),
                    onPrimary = Color(0xFF0E0F13),
                    background = Color(0xFF0E0F13),
                    surface = Color(0xFF1A1C22),
                    onSurface = Color.White,
                    onBackground = Color.White
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (mode == "breathing") {
                            BreathingStep(onDone = { openApp() }, onNotNow = { notNow() })
                        } else {
                            MathStep(onDone = { openApp() }, onNotNow = { notNow() })
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "Not opened: ${FocusPrefs(this@OpenCheckActivity).notOpenedTotal(pkg)} times",
                            fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.Gray
                        )
                    }
                }
            }
        }
    }

    private fun openApp() {
        FocusPrefs(this).setOpenApproved(true)
        finish()
    }

    private fun notNow() {
        FocusPrefs(this).addNotOpened(pkg)
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

/** Two rounds of inhale and exhale. Continue appears once both rounds are done. */
@Composable
private fun BreathingStep(onDone: () -> Unit, onNotNow: () -> Unit) {
    var inhale by remember { mutableStateOf(true) }
    var rounds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        repeat(2) {
            inhale = true
            delay(4_000L)
            inhale = false
            delay(6_000L)
            rounds++
        }
    }
    val circle by animateDpAsState(
        targetValue = if (inhale) 220.dp else 110.dp,
        animationSpec = tween(durationMillis = if (inhale) 4_000 else 6_000),
        label = "breath"
    )
    Text("Breathe first", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(28.dp))
    Box(Modifier.size(circle).background(Color(0xFFA9A6F7), CircleShape))
    Spacer(Modifier.height(28.dp))
    Text(if (inhale) "Inhale" else "Exhale", fontSize = 20.sp)
    Spacer(Modifier.height(28.dp))
    if (rounds >= 2) {
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
    }
    TextButton(onClick = onNotNow) { Text("Not now", color = Color.Gray) }
}

/** A maths question. You type the right answer to open the app. */
@Composable
private fun MathStep(onDone: () -> Unit, onNotNow: () -> Unit) {
    val a = remember { (23..97).random() }
    val b = remember { (12..39).random() }
    var typed by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Text("Solve to open", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(24.dp))
    Text("$a × $b = ?", fontSize = 36.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = typed,
        onValueChange = { typed = it.filter { c -> c.isDigit() }.take(6); wrong = false },
        placeholder = { Text("Answer") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
    if (wrong) Text("Not quite. Try again.", color = Color(0xFFD98C8C), fontSize = 13.sp)
    Button(
        enabled = typed.isNotEmpty(),
        onClick = { if (typed.toIntOrNull() == a * b) onDone() else wrong = true },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Open") }
    TextButton(onClick = onNotNow) { Text("Not now", color = Color.Gray) }
}
