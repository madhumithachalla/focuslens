package com.focuslens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class LearnTopic(val label: String) {
    ALL("All"),
    FOCUS_PRODUCTIVITY("Focus & Productivity"),
    SLEEP("Sleep"),
    MENTAL_HEALTH("Mental Health"),
    HABITS("Habits & Behavior"),
    STUDY_SKILLS("Study Skills"),
    RESEARCH("Research & Papers")
}

data class LearnItem(
    val id: String,
    val title: String,
    val source: String,
    val url: String,
    val topic: LearnTopic,
    /** How many reflection prompts to answer before "Mark as read" unlocks — roughly how dense the material is. */
    val complexity: Int,
    val kind: String
)

// Established, stable organizations only, and homepage/hub-level links where I can't
// confirm a specific deep page resolves from this sandbox (no live browser here) — same
// policy as the original Reading list: a verified real front page beats a guessed URL
// that might be dead.
private val LEARN_ITEMS = listOf(
    LearnItem("who_mental_health", "Mental health of adolescents (fact sheet)", "World Health Organization",
        "https://www.who.int/news-room/fact-sheets/detail/mental-health-of-adolescents", LearnTopic.MENTAL_HEALTH, 1, "Article"),
    LearnItem("nhs_every_mind", "Every Mind Matters", "NHS",
        "https://www.nhs.uk/every-mind-matters/", LearnTopic.MENTAL_HEALTH, 1, "Org hub"),
    LearnItem("mind_uk", "Information and support", "Mind (UK mental health charity)",
        "https://www.mind.org.uk/", LearnTopic.MENTAL_HEALTH, 1, "Org hub"),
    LearnItem("apa", "Stress, coping and everyday psychology", "American Psychological Association",
        "https://www.apa.org/", LearnTopic.MENTAL_HEALTH, 2, "Org hub"),
    LearnItem("digital_wellbeing", "Digital Wellbeing", "Google",
        "https://wellbeing.google/", LearnTopic.FOCUS_PRODUCTIVITY, 1, "Org hub"),
    LearnItem("headspace", "Mindfulness and focus", "Headspace",
        "https://www.headspace.com/", LearnTopic.FOCUS_PRODUCTIVITY, 1, "Org hub"),
    LearnItem("newsinhealth", "News in Health", "NIH (National Institutes of Health)",
        "https://newsinhealth.nih.gov/", LearnTopic.FOCUS_PRODUCTIVITY, 2, "Newsletter"),
    LearnItem("cdc_sleep", "Sleep and health", "CDC",
        "https://www.cdc.gov/sleep/", LearnTopic.SLEEP, 1, "Article"),
    LearnItem("fs_blog", "Brain Food", "Farnam Street",
        "https://fs.blog/", LearnTopic.HABITS, 2, "Newsletter"),
    LearnItem("james_clear", "3-2-1 Thursday", "James Clear",
        "https://jamesclear.com/", LearnTopic.HABITS, 2, "Newsletter"),
    LearnItem("marginalian", "The Marginalian", "Maria Popova",
        "https://www.themarginalian.org/", LearnTopic.HABITS, 2, "Newsletter"),
    LearnItem("khan_academy", "Free courses on anything", "Khan Academy",
        "https://www.khanacademy.org/", LearnTopic.STUDY_SKILLS, 1, "Org hub"),
    LearnItem("learning_how_to_learn", "Learning How to Learn (course)", "Coursera / UC San Diego",
        "https://www.coursera.org/learn/learning-how-to-learn", LearnTopic.STUDY_SKILLS, 2, "Course"),
    LearnItem("scholar", "Search real research papers", "Google Scholar",
        "https://scholar.google.com/", LearnTopic.RESEARCH, 3, "Research hub"),
    LearnItem("pubmed", "Biomedical and life-science research", "PubMed",
        "https://pubmed.ncbi.nlm.nih.gov/", LearnTopic.RESEARCH, 3, "Research hub"),
    LearnItem("jstor", "Academic journals and papers", "JSTOR",
        "https://www.jstor.org/", LearnTopic.RESEARCH, 3, "Research hub")
)

private val REFLECTION_PROMPTS = listOf(
    "What's one idea from this you want to remember?",
    "What's the main claim, in your own words?",
    "What's one limitation or counterpoint worth keeping in mind?"
)

@Composable
fun LearnScreen() {
    val context = LocalContext.current
    val prefs = remember { LearnPrefs(context) }
    var rev by remember { mutableIntStateOf(0) }
    var topic by remember { mutableStateOf(LearnTopic.ALL) }
    var confirmItem by remember { mutableStateOf<LearnItem?>(null) }

    val readCount = remember(rev) { prefs.totalRead(LEARN_ITEMS.map { it.id }) }
    val visible = remember(topic) {
        if (topic == LearnTopic.ALL) LEARN_ITEMS else LEARN_ITEMS.filter { it.topic == topic }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Spacer(Modifier.height(16.dp))
                Text("Learn", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Newsletters, articles and research hubs to learn from, on your own schedule — " +
                        "a few honest starting points, not a feed. $readCount of ${LEARN_ITEMS.size} marked read.",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
        }

        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LearnTopic.values().forEach { t ->
                    FilterChip(selected = topic == t, onClick = { topic = t }, label = { Text(t.label) })
                }
            }
        }

        items(visible, key = { it.id }) { item ->
            val isRead = remember(rev, item.id) { prefs.isRead(item.id) }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                        }) {
                            Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(2.dp))
                            Text("${item.source} · ${item.kind} · ${item.topic.label}", fontSize = 11.sp, color = Color.Gray)
                        }
                        if (isRead) {
                            Text("✓ Read", fontSize = 12.sp, color = Color(0xFF2E7D5B), fontWeight = FontWeight.Medium)
                        }
                    }
                    if (!isRead) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { confirmItem = item }) {
                            Text("Mark as read")
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    val target = confirmItem
    if (target != null) {
        ReflectionDialog(
            item = target,
            onConfirm = {
                prefs.markRead(target.id)
                confirmItem = null
                rev++
            },
            onDismiss = { confirmItem = null }
        )
    }
}

/**
 * Before "Mark as read" sticks, answer a short prompt per [LearnItem.complexity] — more for
 * a research hub than a short newsletter. This is a self-reflection nudge, not a graded
 * comprehension check (there's no way to verify an answer against the source from here):
 * answers only have to be non-empty to continue, and are discarded immediately, never stored.
 */
@Composable
private fun ReflectionDialog(item: LearnItem, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val prompts = REFLECTION_PROMPTS.take(item.complexity)
    val answers = remember { mutableStateListOf(*Array(prompts.size) { "" }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Before you mark \"${item.title}\" read") },
        text = {
            Column {
                Text(
                    "Quick self-check, not graded — just enough to make sure this wasn't a blind tap.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.height(10.dp))
                prompts.forEachIndexed { i, prompt ->
                    Text(prompt, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = answers[i],
                        onValueChange = { answers[i] = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("A sentence is enough", fontSize = 12.sp) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = answers.all { it.trim().length >= 3 }
            ) { Text("Mark as read") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not yet") } }
    )
}
