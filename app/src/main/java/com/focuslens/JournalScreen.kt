package com.focuslens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslens.data.AppDatabase
import com.focuslens.data.JournalEntryEntity
import com.focuslens.data.JournalRepository
import kotlinx.coroutines.launch

private val PROMPTS = listOf(
    "🧠" to "What's one thing that took up space in your head today, even if it didn't need to?",
    "🌤️" to "When did you feel most like yourself today?",
    "🪨" to "What felt heavy today, and what's one small way to put it down?"
)
private val MOODS = listOf("😔" to "low", "😐" to "flat", "🙂" to "okay", "😊" to "good", "🤩" to "great")

/** A starting structure, not a question — unlike PROMPTS, these pre-fill the text field so
 *  there's a shape to write into rather than a blank box. Still just text underneath: nothing
 *  about saving or reading an entry back treats a template differently from free writing. */
private data class JournalTemplate(val emoji: String, val title: String, val scaffold: String)

private val TEMPLATES = listOf(
    JournalTemplate("🙏", "Gratitude list", "Three things I'm grateful for today:\n1. \n2. \n3. \n"),
    JournalTemplate("✅", "Daily reflection", "What went well today:\n\nWhat didn't go as planned:\n\nOne thing to try tomorrow:\n"),
    JournalTemplate("🧩", "Thought record", "Situation:\n\nWhat I thought in the moment:\n\nHow strongly I felt it (1–10):\n\nA more balanced way to see it:\n"),
    JournalTemplate("🌊", "Brain dump", "Just getting everything out, no particular order:\n\n")
)

@Composable
fun JournalScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val repo = remember { JournalRepository(db.journalDao()) }
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<List<JournalEntryEntity>>(emptyList()) }
    var streak by remember { mutableIntStateOf(0) }
    var writing by remember { mutableStateOf(false) }
    var activePrompt by remember { mutableStateOf<String?>(null) }
    var activeTemplateTitle by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf<String?>(null) }
    var celebration by remember { mutableStateOf<Int?>(null) }

    suspend fun refresh() {
        entries = repo.recent(30)
        streak = repo.currentStreak()
    }

    LaunchedEffect(Unit) { refresh() }

    celebration?.let { milestone ->
        CelebrationDialog(
            title = "$milestone-day journal streak",
            body = "You've written an entry $milestone days running. That's a real record building up, not just a number.",
            onDismiss = { celebration = null }
        )
    }

    if (writing) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    writing = false
                    text = ""
                    mood = null
                    activePrompt = null
                    activeTemplateTitle = null
                }) { Text("← Back") }
            }
            Spacer(Modifier.height(4.dp))
            activePrompt?.let {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("PROMPT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(it, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            activeTemplateTitle?.let {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("TEMPLATE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(it, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Start writing...") },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            Spacer(Modifier.height(12.dp))
            Text("How does this feel?", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MOODS.forEach { (emoji, id) ->
                    FilterChip(selected = mood == id, onClick = { mood = id }, label = { Text(emoji) })
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                enabled = text.isNotBlank(),
                onClick = {
                    scope.launch {
                        repo.save(activePrompt, text, mood)
                        refresh()
                        repo.milestoneFor(streak)?.let { celebration = it }
                        writing = false
                        text = ""
                        mood = null
                        activePrompt = null
                        activeTemplateTitle = null
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save entry") }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Spacer(Modifier.height(16.dp))
                Text("Journal", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (streak > 0) "$streak day streak · private to you, never leaves your device" else "Private to you — nothing here leaves your device",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
        }

        item {
            Text("TODAY'S PROMPTS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        }
        items(PROMPTS) { (emoji, prompt) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { activePrompt = prompt; writing = true }
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(emoji, fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(prompt, fontSize = 13.sp)
                }
            }
        }

        item {
            Button(
                onClick = { activePrompt = null; activeTemplateTitle = null; text = ""; writing = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("✏️ Just write freely (blank page)") }
        }

        item {
            Spacer(Modifier.height(10.dp))
            Text("TEMPLATES", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        }
        items(TEMPLATES) { t ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    activePrompt = null
                    activeTemplateTitle = t.title
                    text = t.scaffold
                    writing = true
                }
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t.emoji, fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(t.title, fontSize = 13.sp)
                }
            }
        }

        item {
            Spacer(Modifier.height(10.dp))
            Text("PAST ENTRIES", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        }

        if (entries.isEmpty()) {
            item { Text("Nothing logged yet.", fontSize = 13.sp, color = Color.Gray) }
        }

        items(entries, key = { it.id }) { entry ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(formatJournalDate(entry.date), fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                        entry.mood?.let { m -> Text(MOODS.firstOrNull { it.second == m }?.first ?: "", fontSize = 12.sp) }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(entry.text, fontSize = 13.sp, maxLines = 3)
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun formatJournalDate(yyyymmdd: String): String {
    if (yyyymmdd.length != 8) return yyyymmdd
    val month = yyyymmdd.substring(4, 6).toInt()
    val day = yyyymmdd.substring(6, 8).toInt()
    val names = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    return "${names[month - 1]} $day"
}
