package com.focuslens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

enum class Challenge { BREATHE, MATH, PHRASE }

/**
 * The pause screen shown over a blocked app. Passing the challenge opens the app for a
 * short time. The more you try to open it, the harder the challenge gets.
 */
class BlockActivity : ComponentActivity() {

    companion object {
        @Volatile
        var showing = false
    }

    private var pkg: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pkg = intent.getStringExtra("pkg") ?: ""
        if (pkg.isEmpty()) {
            finish()
            return
        }
        showing = true

        val prefs = FocusPrefs(this)
        val repo = UsageRepository(this)
        val label = repo.labelFor(pkg)
        // The service passes the minutes it already knows; querying here blocked the screen.
        // Show the figure the service already has right away, then refresh it off the main thread.
        var usedMin by mutableLongStateOf(if (intent.hasExtra("usedMin")) intent.getLongExtra("usedMin", 0L) else 0L)
        CoroutineScope(Dispatchers.IO).launch {
            val fresh = try {
                (repo.loadCustom(FocusPrefs.limitDayStartMs(), System.currentTimeMillis(), withLaunches = false)
                    .firstOrNull { it.packageName == pkg }?.millis ?: 0L) / 60_000L
            } catch (e: Exception) {
                null
            }
            if (fresh != null) withContext(Dispatchers.Main) { usedMin = fresh }
        }
        val limit = prefs.limitMin(pkg)
        val over = usedMin >= limit
        val passes = prefs.passesToday(pkg)
        val resisted = prefs.gaveUpToday(pkg)
        val maxSkips = prefs.maxSkipsPerDay(pkg)

        // Two independent hard stops, same idea as Minimalist Focus Launcher's time-based
        // app locks: once either is hit, there is no challenge left to beat today — only
        // BREATHE/MATH/PHRASE earns a few more minutes; neither does once locked.
        val skipLimitReached = passes >= maxSkips
        val locked = skipLimitReached || over

        if (locked) {
            setBrandedContent {
                MaterialTheme(
                    typography = AppTypography,
                    colorScheme = lightColorScheme(
                        primary = Color(0xFF1F3A34),
                        background = Color(0xFFF7F5F0),
                        surface = Color(0xFFF7F5F0)
                    )
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        BackHandler { goHome(prefs) }
                        val qp = QuizPrefs(this@BlockActivity)
                        LockedScreen(
                            label = label,
                            usedMin = usedMin,
                            limit = limit,
                            skipLimitReached = skipLimitReached,
                            maxSkips = maxSkips,
                            resetAt = prefs.nextResetAt(),
                            onGiveUp = { goHome(prefs) },
                            quizGate = qp.gateConfig(),
                            canUnlock = qp.canUnlock(pkg),
                            unlockAllowance = qp.unlocksPerDayAllowed(),
                            unlocksUsed = qp.unlocksLast24h(pkg),
                            unlockMinutes = qp.unlockMinutes(),
                            onUnlocked = {
                                qp.recordUnlock(pkg)
                                prefs.bump(pkg, "chose_open")
                                prefs.grant(pkg, qp.unlockMinutes())
                                prefs.setUnlockUntil(pkg, System.currentTimeMillis() + qp.unlockMinutes() * 60_000L)
                                finish()
                            }
                        )
                    }
                }
            }
            return
        }

        val grantMin = 10

        // Still under budget: a quick acknowledgement, not an obstacle — the challenge/quiz
        // gate is for once you're actually over the limit, same idea as a lot of digital-
        // wellbeing tools (friction scales with how far over you are, not a flat tax on
        // every single open). This is the "Continue" path that was missing entirely before —
        // under budget used to get exactly the same breathing/math/quiz gate as over budget.
        // Grants access without counting as a "pass" — passesToday drives the daily skip
        // limit (see skipLimitReached below), which exists to cap how many times the actual
        // over-budget challenge gets beaten, not to penalize ordinary under-budget opens.
        if (!over) {
            setBrandedContent {
                MaterialTheme(
                    typography = AppTypography,
                    colorScheme = lightColorScheme(
                        primary = Color(0xFF1F3A34),
                        background = Color(0xFFF7F5F0),
                        surface = Color(0xFFF7F5F0)
                    )
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        BackHandler { goHome(prefs) }
                        UnderLimitScreen(
                            label = label,
                            usedMin = usedMin,
                            limit = limit,
                            onContinue = { prefs.bump(pkg, "chose_open"); prefs.grant(pkg, grantMin); finish() },
                            onGiveUp = { goHome(prefs) }
                        )
                    }
                }
            }
            return
        }

        val steps = when {
            passes == 0 -> listOf(Challenge.BREATHE)
            passes == 1 -> listOf(Challenge.BREATHE, Challenge.MATH)
            else -> listOf(Challenge.MATH, Challenge.PHRASE)
        }

        // The quiz gate comes before the allow path. Each time it is shown counts as one attempt
        // today (read the config first so the first attempt starts at level 1).
        val quizPrefs = QuizPrefs(this)
        val quizGate = quizPrefs.gateConfig()
        if (quizGate != null && savedInstanceState == null) quizPrefs.addAttempt()

        setBrandedContent {
            MaterialTheme(
                typography = AppTypography,
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1F3A34),
                    background = Color(0xFFF7F5F0),
                    surface = Color(0xFFF7F5F0)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BackHandler { goHome(prefs) }
                    BlockScreen(
                        label = label,
                        usedMin = usedMin,
                        limit = limit,
                        over = over,
                        opensToday = passes,
                        resisted = resisted,
                        steps = steps,
                        grantMin = grantMin,
                        quizGate = quizGate,
                        onPass = {
                            prefs.bump(pkg, "chose_open")
                            prefs.grant(pkg, grantMin)
                            prefs.addPass(pkg)
                            finish()
                        },
                        onGiveUp = { goHome(prefs) }
                    )
                }
            }
        }
    }

    private fun goHome(prefs: FocusPrefs) {
        prefs.bump(pkg, "chose_leave")
        prefs.addGaveUp(pkg)
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        finish()
    }

    override fun onStop() {
        // If the user leaves this screen any other way, close it so it cannot be used
        // to hold the "already showing" state and skip the block next time.
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
fun BlockScreen(
    label: String,
    usedMin: Long,
    limit: Int,
    over: Boolean,
    opensToday: Int,
    resisted: Int,
    steps: List<Challenge>,
    grantMin: Int,
    onPass: () -> Unit,
    onGiveUp: () -> Unit,
    quizGate: QuizGateConfig? = null
) {
    var step by remember { mutableIntStateOf(0) }
    val next: () -> Unit = {
        if (step + 1 >= steps.size) {
            onPass()
        } else {
            step++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Pause.", fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            if (over) {
                "You have used $label for $usedMin min today. Your limit is $limit min."
            } else {
                "$label is on your block list, so it pauses before it opens. " +
                    "$usedMin of $limit min used today. Time left today: ${Clock.limitLeftHms(limit, usedMin)}. " +
                    "Is this what you want to do right now?"
            },
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        if (opensToday > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "You have already opened it $opensToday time${if (opensToday == 1) "" else "s"} today.",
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
        if (resisted > 0) {
            Text(
                "You chose not to open it $resisted time${if (resisted == 1) "" else "s"} today. Nice.",
                fontSize = 13.sp,
                color = Color(0xFF2E7D5B),
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(28.dp))
        if (quizGate != null) {
            QuizGate(
                config = quizGate,
                accent = Color(0xFF1F3A34),
                onSuccess = onPass,
                onHome = onGiveUp
            )
        } else {
            Text("Step ${step + 1} of ${steps.size}", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(12.dp))

            key(step) {
                when (steps[step]) {
                    Challenge.BREATHE -> BreatheStep(if (over) 15 else 10) { next() }
                    Challenge.MATH -> MathStep { next() }
                    Challenge.PHRASE -> PhraseStep { next() }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Passing gives you $grantMin minutes in $label.",
            fontSize = 12.sp,
            color = Color.Gray
        )
        if (quizGate == null) {
            TextButton(onClick = onGiveUp) {
                Text("Not now, take me home", fontSize = 16.sp)
            }
        }
    }
}

/** Shown while still under the daily limit — the "Continue" path. A brief check-in, not an
 *  obstacle; the real friction (BlockScreen/LockedScreen) is reserved for once the budget's
 *  actually spent. */
@Composable
fun UnderLimitScreen(label: String, usedMin: Long, limit: Int, onContinue: () -> Unit, onGiveUp: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "$usedMin of $limit min used today.",
            fontSize = 16.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth(0.7f)) {
            Text("Continue", fontSize = 16.sp)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onGiveUp) {
            Text("Actually, take me home", fontSize = 14.sp, color = Color.Gray)
        }
    }
}

/** No challenge, no bypass — the daily allotment (skips or time) is spent. */
@Composable
fun LockedScreen(
    label: String,
    usedMin: Long,
    limit: Int,
    skipLimitReached: Boolean,
    maxSkips: Int,
    resetAt: Long,
    onGiveUp: () -> Unit,
    quizGate: QuizGateConfig? = null,
    canUnlock: Boolean = false,
    unlockAllowance: Int = 1,
    unlocksUsed: Int = 0,
    unlockMinutes: Int = 5,
    onUnlocked: () -> Unit = {}
) {
    var showGate by remember { mutableStateOf(false) }
    if (showGate && quizGate != null) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Text("Answer to unlock $label for $unlockMinutes minutes", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            QuizGate(
                config = quizGate,
                accent = Color(0xFF1F3A34),
                onSuccess = onUnlocked,
                onHome = onGiveUp
            )
        }
        return
    }

    // A live countdown in hours, minutes and seconds, ticking every second.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            now = System.currentTimeMillis()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🔒", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text("Locked until 6am", fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            if (skipLimitReached) {
                "You've used all $maxSkips of today's skips for $label. No challenge gets you back in."
            } else {
                "You've reached today's $limit min limit on $label ($usedMin min used). It's locked, not just harder."
            },
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Unlocks at 6am. Time left: ${Clock.untilHms(resetAt, now)}",
            fontSize = 13.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(20.dp))
        if (quizGate != null) {
            Text(
                "Unlocks used in the last 24 hours: $unlocksUsed of $unlockAllowance",
                fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Button(enabled = canUnlock, onClick = { showGate = true }) {
                Text(
                    if (canUnlock) "Unlock for $unlockMinutes min (quiz)"
                    else "No unlocks left for 24 hours",
                    fontSize = 15.sp
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        TextButton(onClick = onGiveUp) {
            Text("Take me home", fontSize = 16.sp)
        }
    }
}

/** The guided breath: each step has a name, a length and a colour. */
private enum class BreathPhase(val label: String, val seconds: Int, val color: Color, val size: Float) {
    IN("Breathe in", 4, Color(0xFF2E7D32), 1f),
    HOLD("Hold", 4, Color(0xFFB26A00), 1f),
    OUT("Breathe out", 6, Color(0xFF1565C0), 0.55f)
}

@Composable
fun BreatheStep(seconds: Int, onDone: () -> Unit) {
    val phases = BreathPhase.values()
    var left by remember { mutableIntStateOf(seconds) }
    var phaseIndex by remember { mutableIntStateOf(0) }
    var phaseLeft by remember { mutableIntStateOf(phases[0].seconds) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000L)
            left--
            phaseLeft--
            if (phaseLeft <= 0) {
                phaseIndex = (phaseIndex + 1) % phases.size
                phaseLeft = phases[phaseIndex].seconds
            }
        }
    }
    val phase = phases[phaseIndex]
    val size by androidx.compose.animation.core.animateFloatAsState(
        targetValue = phase.size,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = phase.seconds * 1000),
        label = "breathSize"
    )
    val color by androidx.compose.animation.animateColorAsState(
        targetValue = phase.color,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500),
        label = "breathColor"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(phase.label, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
        Text("$phaseLeft s", fontSize = 16.sp, color = color)
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .size(180.dp)
                .scale(size)
                .background(color, CircleShape)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "How to do it: 1. Breathe in slowly through your nose for 4 seconds. " +
                "2. Hold your breath for 4 seconds. 3. Breathe out slowly through your mouth for 6 seconds. " +
                "Then start again.",
            fontSize = 14.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (left > 0) "Keep going for ${left}s" else "Well done. You can continue now.",
            fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onDone, enabled = left == 0) {
            Text(if (left > 0) "Wait ${left}s" else "Continue")
        }
    }
}

private fun newQuestion(): Pair<String, Int> {
    val a = Random.nextInt(12, 60)
    val b = Random.nextInt(3, 9)
    val c = Random.nextInt(10, 90)
    return "$a × $b + $c" to (a * b + c)
}

@Composable
fun MathStep(onDone: () -> Unit) {
    var solved by remember { mutableIntStateOf(0) }
    var question by remember { mutableStateOf(newQuestion()) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Solve 3 to continue ($solved/3)", fontSize = 15.sp)
        Spacer(Modifier.height(12.dp))
        Text(question.first, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it.filter { ch -> ch.isDigit() }.take(5)
                wrong = false
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(0.6f),
            isError = wrong
        )
        if (wrong) {
            Text("Not quite. Here is a new one.", fontSize = 12.sp, color = Color(0xFFB3261E))
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (input.toIntOrNull() == question.second) {
                    solved++
                    input = ""
                    if (solved >= 3) onDone() else question = newQuestion()
                } else {
                    wrong = true
                    input = ""
                    question = newQuestion()
                }
            },
            enabled = input.isNotEmpty()
        ) {
            Text("Check")
        }
    }
}

private const val PHRASE = "I choose how I spend my time"

@Composable
fun PhraseStep(onDone: () -> Unit) {
    var input by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Type this sentence exactly:", fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Text(PHRASE, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onDone,
            enabled = input.trim().equals(PHRASE, ignoreCase = true)
        ) {
            Text("Continue")
        }
    }
}
