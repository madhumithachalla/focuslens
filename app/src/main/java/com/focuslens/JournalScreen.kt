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

// Same prompt library as the web app (docs/content.js JOURNAL_CATEGORIES), minus the
// category browsing UI — a flat, varied pool instead of three fixed questions.
private val PROMPTS = listOf(
    "🪞" to "What did I spend the most energy on today, and was it worth it?",
    "🪞" to "What am I avoiding, and what is the smallest first step?",
    "🙏" to "Who made my day easier recently, and what could I say to thank them?",
    "🙏" to "What went right today that I didn't plan for?",
    "🌊" to "What am I feeling right now, and where do I feel it in my body?",
    "🌊" to "If a friend told me this worry, what would I say to them?",
    "🌊" to "What is in my control here, and what isn't?",
    "🏛️" to "What kind of person did I want to be today, and how close did I get?",
    "🏛️" to "Which of my values did I honour today, and which did I neglect?",
    "🤝" to "Is there a conversation I've been putting off? What's the kindest honest version of it?",
    "🎓" to "What's blocking me on my biggest task, and who or what could help?",
    "⚡" to "What helped me start today, and what made starting hard?",
    "⚡" to "What did I hyperfocus on, and was it what I intended?",
    "💚" to "What did I do well today that I'm not giving myself credit for?",
    "💚" to "What can I forgive myself for this week?",
    "🕊️" to "What is one small, gentle thing I can do for myself in the next hour?"
)
private val MOODS = listOf("😔" to "low", "😐" to "flat", "🙂" to "okay", "😊" to "good", "🤩" to "great")

/** A starting structure, not a question — unlike PROMPTS, these pre-fill the text field so
 *  there's a shape to write into rather than a blank box. Still just text underneath: nothing
 *  about saving or reading an entry back treats a template differently from free writing. */
private data class JournalTemplate(val emoji: String, val title: String, val scaffold: String)

// Same set as the web app (docs/content.js JOURNAL_TEMPLATES) for parity across platforms.
private val TEMPLATES = listOf(
    JournalTemplate("🙏", "Gratitude list", "Three things I'm grateful for today:\n1. \n2. \n3. \n"),
    JournalTemplate("✅", "Daily reflection", "What went well today:\n\nWhat didn't go as planned:\n\nOne thing to try tomorrow:\n"),
    JournalTemplate("🧩", "Thought record (CBT)", "Situation:\n\nWhat I thought in the moment:\n\nHow strongly I felt it (1–10):\n\nEvidence for the thought:\n\nEvidence against it:\n\nA more balanced way to see it:\n"),
    JournalTemplate("🌊", "Brain dump (ADHD)", "Everything in my head, no order, no judging:\n\n\nPick ONE thing from above to do next:\n"),
    JournalTemplate("🏛️", "Stoic evening review", "What did I do well today?\n\nWhere did I fall short of who I want to be?\n\nWhat was in my control, and what wasn't?\n\nWhat will I do differently tomorrow?\n"),
    JournalTemplate("🌓", "Shadow work", "Something that really irritated me lately:\n\nWhat might it be touching in me?\n\nA quality I judge in others that I recognise in myself:\n\nHow could I treat that part of me with kindness?\n"),
    JournalTemplate("🌙", "Dream log", "What I remember of the dream:\n\nHow it felt:\n\nAnything from yesterday it might connect to:\n"),
    JournalTemplate("🌆", "Evening wind-down", "What's still on my mind:\n\nWhat can wait until tomorrow:\n\nOne thing that felt good today:\n\nTomorrow's first small step:\n"),
    JournalTemplate("⏱️", "5-minute micro-journal", "Mood in one word:\n\nOne win:\n\nOne worry:\n\nOne kind thing I can do for myself:\n"),
    JournalTemplate("🔎", "Sensory & focus check-in", "What pulled my attention today (apps, people, noise)?\n\nWhat helped me concentrate?\n\nAny sensory triggers (light, sound, texture, crowds)?\n\nOne adjustment for tomorrow:\n")
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
    var suggestedPrompts by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    suspend fun refresh() {
        entries = repo.recent(30)
        streak = repo.currentStreak()
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(Unit) {
        suggestedPrompts = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { usageAwareJournalPrompts(context) }
    }

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
                Text("Journal", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (streak > 0) "$streak day streak · private to you, never leaves your device" else "Private to you — nothing here leaves your device",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
        }

        if (suggestedPrompts.isNotEmpty()) {
            item {
                Text("SUGGESTED FOR YOU", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(Modifier.height(2.dp))
                Text(
                    "From your own usage this week — not a fixed list.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
            items(suggestedPrompts) { (emoji, prompt) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    onClick = { activePrompt = prompt; writing = true }
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(emoji, fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(prompt, fontSize = 13.sp)
                    }
                }
            }
            item { Spacer(Modifier.height(6.dp)) }
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
