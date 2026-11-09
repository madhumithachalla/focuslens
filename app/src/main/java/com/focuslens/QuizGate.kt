package com.focuslens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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

/** Everything one run of the gate needs. Build it with QuizPrefs.gateConfig(). */
data class QuizGateConfig(
    val categories: List<String>,
    val needed: Int,
    val delaySeconds: Int,
    val attemptsToday: Int,
    val custom: List<CustomQuestion>,
    val caps: Map<String, Int> = emptyMap(),
    val usedToday: Map<String, Int> = emptyMap(),
    val askedKeys: Set<String> = emptySet()
)

/**
 * The question gate: a countdown, then [QuizGateConfig.needed] correct answers (4 choices each,
 * with feedback and an explanation) before [onSuccess] is called. [onHome] is always available.
 * [accent] is the text/button colour to use on the host screen's background.
 */
@Composable
fun QuizGate(
    config: QuizGateConfig,
    accent: Color,
    onSuccess: () -> Unit,
    onHome: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val logPrefs = remember { QuizPrefs(context) }
    val session = remember {
        QuizSession(
            categories = config.categories,
            attemptsToday = config.attemptsToday,
            needed = config.needed,
            customQuestions = config.custom,
            askedKeys = config.askedKeys,
            categoryCaps = config.caps,
            usedToday = config.usedToday
        )
    }
    // Asks the next question and logs it, so it is not asked again today.
    fun nextQuestion(): QuizQuestion? {
        val nq = session.next()
        if (nq != null) logPrefs.recordAsked(nq.category, nq.prompt)
        return nq
    }
    var remaining by remember { mutableIntStateOf(config.delaySeconds) }
    var question by remember { mutableStateOf<QuizQuestion?>(null) }
    var selected by remember { mutableIntStateOf(-1) }
    var wasRight by remember { mutableStateOf(false) }
    var explain by remember { mutableStateOf("") }
    var correct by remember { mutableIntStateOf(0) }
    var level by remember { mutableIntStateOf(session.level) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining -= 1
        }
        question = nextQuestion()
    }

    val muted = accent.copy(alpha = 0.7f)
    val q = question

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (q == null && session.exhausted) {
            Text(
                "You've used every question left for today in these categories. " +
                    "Raise a category limit in Quiz settings, or turn a category on.",
                fontSize = 15.sp, color = accent, textAlign = TextAlign.Center
            )
        } else if (q == null) {
            Text(
                "Take a breath. Your first question comes in ${remaining}s.",
                fontSize = 15.sp, color = accent, textAlign = TextAlign.Center
            )
        } else {
            Text(
                "Answer ${session.needed} correctly to continue ($correct/${session.needed})",
                fontSize = 13.sp, color = muted
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { correct.toFloat() / session.needed.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF2E7D5B),
                trackColor = muted.copy(alpha = 0.25f)
            )
            Spacer(Modifier.height(8.dp))
            Text("Difficulty $level of $QUIZ_MAX_LEVEL  " + "●".repeat(level) + "○".repeat(QUIZ_MAX_LEVEL - level),
                fontSize = 12.sp, color = muted)
            Spacer(Modifier.height(14.dp))
            Text(q.prompt, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = accent, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))

            val answered = selected >= 0
            q.choices.forEachIndexed { i, choice ->
                val border = when {
                    !answered -> accent
                    i == q.answerIndex -> Color(0xFF2E7D5B)
                    i == selected -> Color(0xFFB3261E)
                    else -> muted
                }
                OutlinedButton(
                    onClick = {
                        if (!answered && !finished) {
                            val r = session.answer(i)
                            selected = i
                            wasRight = r.right
                            explain = r.explain
                            correct = session.correct
                            level = session.level
                            finished = r.done
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = border)
                ) {
                    Text(choice, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
            }

            if (answered) {
                Spacer(Modifier.height(10.dp))
                Text(
                    if (wasRight) "Correct." else "Not quite.",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (wasRight) Color(0xFF2E7D5B) else Color(0xFFB3261E)
                )
                if (explain.isNotEmpty()) {
                    Text(explain, fontSize = 13.sp, color = muted, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(10.dp))
                if (finished) {
                    Button(onClick = onSuccess) { Text("Continue") }
                } else {
                    Button(onClick = {
                        selected = -1
                        question = nextQuestion()
                    }) { Text("Next question") }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.Center) {
            Button(onClick = onHome) { Text("TAKE ME OUT OF HERE") }
        }
    }
}
