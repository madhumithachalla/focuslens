package com.focuslens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Settings for the question gate shown before a blocked app opens. */
@Composable
fun QuizSettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { QuizPrefs(context) }

    var caps by remember { mutableStateOf(QUIZ_CATEGORIES.associate { it.id to prefs.categoryCap(it.id) }) }
    var passes by remember { mutableIntStateOf(prefs.passesPerDay()) }
    var unlockAllowance by remember { mutableIntStateOf(prefs.unlocksPerDayAllowed()) }
    var unlockMinutes by remember { mutableIntStateOf(prefs.unlockMinutes()) }
    var custom by remember { mutableStateOf(prefs.customQuestions()) }

    var newPrompt by remember { mutableStateOf("") }
    var newCorrect by remember { mutableStateOf("") }
    var newWrong1 by remember { mutableStateOf("") }
    var newWrong2 by remember { mutableStateOf("") }
    var newWrong3 by remember { mutableStateOf("") }
    var newExplain by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("Quiz gate", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "The quiz is fixed: 3 correct answers, a 10-second wait, and questions never repeat, " +
                    "so the same question is not asked again on a later day. Only the limits below can be changed.",
                fontSize = 13.sp,
                color = Color.Gray
            )
            Text("Attempts today: ${prefs.attemptsToday()}", fontSize = 13.sp, color = Color.Gray)
        }

        item { QuizLabel("TEMPORARY UNLOCK") }
        item {
            Text(
                "Answer the quiz (or one of your own questions) to open a blocked app for a short time.",
                fontSize = 12.sp, color = Color.Gray
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Unlocks per app per 24 hours (default 1, max 3)", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                QuizStepper(unlockAllowance, 1, 3) {
                    prefs.setUnlocksPerDayAllowed(it)
                    unlockAllowance = prefs.unlocksPerDayAllowed()
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Minutes unlocked (default 5)", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                QuizStepper(unlockMinutes, 1, 10) {
                    prefs.setUnlockMinutes(it)
                    unlockMinutes = prefs.unlockMinutes()
                }
            }
        }

        item { QuizLabel("MAX QUESTIONS PER DAY, BY CATEGORY (0 = no limit)") }
        items(QUIZ_CATEGORIES) { c ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(c.label, fontSize = 15.sp, modifier = Modifier.weight(1f))
                QuizStepper(caps[c.id] ?: 0, 0, 200) { v ->
                    prefs.setCategoryCap(c.id, v)
                    caps = caps + (c.id to v)
                }
            }
        }

        item {
            QuizLabel("EMERGENCY PASSES PER DAY")
            QuizStepper(passes, 0, 20) {
                prefs.setPassesPerDay(it)
                passes = prefs.passesPerDay()
            }
        }

        item { QuizLabel("YOUR OWN QUESTIONS (MIXED IN ONE IN FOUR)") }
        if (custom.isEmpty()) {
            item { Text("None yet.", fontSize = 13.sp, color = Color.Gray) }
        }
        itemsIndexed(custom) { i, q ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(q.prompt, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("Answer: ${q.choices.getOrElse(q.answerIndex) { "" }}", fontSize = 12.sp, color = Color.Gray)
                    }
                    TextButton(onClick = {
                        prefs.deleteCustomQuestion(i)
                        custom = prefs.customQuestions()
                    }) { Text("Delete") }
                }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Text("Add a question", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            OutlinedTextField(newPrompt, { newPrompt = it }, label = { Text("Question") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(newCorrect, { newCorrect = it }, label = { Text("Correct answer") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(newWrong1, { newWrong1 = it }, label = { Text("Wrong answer 1") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(newWrong2, { newWrong2 = it }, label = { Text("Wrong answer 2") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(newWrong3, { newWrong3 = it }, label = { Text("Wrong answer 3") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(newExplain, { newExplain = it }, label = { Text("Explanation (optional)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            val wrongs = listOf(newWrong1, newWrong2, newWrong3).map { it.trim() }
            val valid = newPrompt.isNotBlank() && newCorrect.isNotBlank() && wrongs.all { it.isNotEmpty() } &&
                (wrongs + newCorrect.trim()).toSet().size == 4
            Button(
                enabled = valid,
                onClick = {
                    // Shuffle once so the right answer isn't always in the same slot.
                    val choices = (wrongs + newCorrect.trim()).shuffled()
                    prefs.addCustomQuestion(
                        CustomQuestion(newPrompt.trim(), choices, choices.indexOf(newCorrect.trim()), newExplain.trim())
                    )
                    custom = prefs.customQuestions()
                    newPrompt = ""; newCorrect = ""; newWrong1 = ""; newWrong2 = ""; newWrong3 = ""; newExplain = ""
                }
            ) { Text("Add question") }
            Text("All four answers must be different.", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QuizLabel(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun QuizStepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    WheelPicker(value = value, min = min, max = max, onChange = onChange)
}
