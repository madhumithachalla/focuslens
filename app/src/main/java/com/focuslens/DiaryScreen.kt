package com.focuslens

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** A starting layout for an entry. The person can change any text; the prompts are only a guide. */
data class DiaryTemplate(val id: String, val name: String, val prompts: List<String>)

val DIARY_TEMPLATES: List<DiaryTemplate> = listOf(
    DiaryTemplate("daily", "Daily reflection", listOf("How did today go?", "What got in the way?", "What will I do tomorrow?")),
    DiaryTemplate("gratitude", "Gratitude", listOf("Three things I'm grateful for", "Someone who made my day better", "A small good moment")),
    DiaryTemplate("wellness", "Wellness", listOf("How did my body feel today?", "How did my mind feel today?", "What I did for my health", "One kind thing I did for myself")),
    DiaryTemplate("travel", "Travel diary", listOf("Where I was", "Highlights", "Food I tried", "People I met", "Something I'd do again")),
    DiaryTemplate("memories", "Memories", listOf("What happened?", "Who was there?", "How I felt then", "Why it matters to me")),
    DiaryTemplate("monthly", "Monthly review", listOf("Biggest win this month", "What I learned", "What drained me", "What I want next month", "One habit to keep, one to drop")),
    DiaryTemplate("vision", "Vision board", listOf("My vision for the next year", "Goals I'm working toward", "What my ideal day looks like", "Words that describe who I'm becoming")),
    DiaryTemplate("brain", "Brain dump", listOf("Everything on my mind", "Worries to park", "Ideas", "My next small step")),
    DiaryTemplate("dream", "Dream record", listOf("What I dreamed about", "How it felt when I woke up", "People, places or symbols in it", "What it might be telling me")),
    DiaryTemplate("weekly", "Weekly review", listOf("What went well this week?", "What didn't go well?", "One thing to change next week")),
    DiaryTemplate("free", "Free page", listOf("Write anything"))
)

/**
 * One diary entry. [entryDate] is the day the entry is about (UTC midnight of that local date, as the
 * date picker returns it). [createdAt] is when it was first saved.
 */
data class DiaryEntry(
    val id: Long,
    val createdAt: Long,
    val entryDate: Long,
    val templateId: String,
    val title: String,
    val sections: List<Pair<String, String>>,
    val attachments: List<String>
)

/** Newest day first, then newest save first. */
internal fun sortDiary(list: List<DiaryEntry>): List<DiaryEntry> =
    list.sortedWith(compareByDescending<DiaryEntry> { it.entryDate }.thenByDescending { it.createdAt })

/** The local calendar day of [millis], as UTC midnight. Matches the date picker's output. */
internal fun localDayUtc(millis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = millis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun diaryDayLabel(millis: Long): String =
    SimpleDateFormat("EEE d MMM yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(millis))

/**
 * Diary entries are kept as files in the app's private folder, not in the main database,
 * so a change to the main database can never delete them. Attached photos are copied in too.
 */
object DiaryStore {
    private fun dir(c: Context) = File(c.filesDir, "diary").apply { mkdirs() }
    private fun entriesFile(c: Context) = File(dir(c), "entries.json")
    fun attachmentFile(c: Context, name: String) = File(File(dir(c), "att"), name)

    fun load(c: Context): List<DiaryEntry> = try {
        val f = entriesFile(c)
        if (!f.exists()) emptyList() else {
            val arr = JSONArray(f.readText())
            sortDiary((0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val secs = o.getJSONArray("sections")
                val atts = o.getJSONArray("attachments")
                val created = o.getLong("createdAt")
                DiaryEntry(
                    id = o.getLong("id"),
                    createdAt = created,
                    // Entries saved before the date field existed use the day they were written.
                    entryDate = o.optLong("entryDate", 0L).takeIf { it > 0L } ?: localDayUtc(created),
                    templateId = o.getString("templateId"),
                    title = o.getString("title"),
                    sections = (0 until secs.length()).map { j ->
                        val s = secs.getJSONObject(j)
                        s.getString("prompt") to s.getString("text")
                    },
                    attachments = (0 until atts.length()).map { atts.getString(it) }
                )
            })
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveAll(c: Context, list: List<DiaryEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("createdAt", e.createdAt)
                    put("entryDate", e.entryDate)
                    put("templateId", e.templateId)
                    put("title", e.title)
                    put("sections", JSONArray().apply {
                        e.sections.forEach { (p, t) -> put(JSONObject().put("prompt", p).put("text", t)) }
                    })
                    put("attachments", JSONArray().apply { e.attachments.forEach { put(it) } })
                }
            )
        }
        entriesFile(c).writeText(arr.toString())
    }

    /** Copies a picked photo into the diary folder. Returns its file name, or null if it failed. */
    fun addAttachment(c: Context, uri: Uri): String? = try {
        val name = "${System.currentTimeMillis()}_${(0..9999).random()}.jpg"
        val out = File(File(dir(c), "att").apply { mkdirs() }, name)
        c.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
        name
    } catch (e: Exception) {
        null
    }

    fun delete(c: Context, e: DiaryEntry) {
        e.attachments.forEach { attachmentFile(c, it).delete() }
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** One HTML file with every entry and its photos inside it, so it opens anywhere. */
    fun exportHtml(c: Context, list: List<DiaryEntry>): String {
        val fmt = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.US)
        val sb = StringBuilder()
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>My FocusLens diary</title>")
        sb.append("<style>body{font-family:system-ui,Arial,sans-serif;margin:24px;color:#1F2A26;line-height:1.5}")
        sb.append("h1{margin:0 0 16px}h2{margin:0}.entry{border:1px solid #dfe5e1;border-radius:12px;padding:16px;margin:16px 0;break-inside:avoid}")
        sb.append(".meta{color:#6b736f;font-size:13px}.prompt{color:#6b736f;font-size:13px;margin-top:10px}img{max-width:100%;border-radius:8px;margin:6px 0}")
        sb.append("</style></head><body><h1>My diary</h1>")
        list.forEach { e ->
            val template = DIARY_TEMPLATES.firstOrNull { it.id == e.templateId }?.name ?: "Entry"
            sb.append("<div class=\"entry\"><h2>${esc(e.title.ifBlank { template })}</h2>")
            sb.append("<div class=\"meta\">${esc(diaryDayLabel(e.entryDate))} · written ${esc(fmt.format(Date(e.createdAt)))} · ${esc(template)}</div>")
            e.sections.forEach { (prompt, text) ->
                if (text.isNotBlank()) {
                    sb.append("<div class=\"prompt\">${esc(prompt)}</div><div>${esc(text).replace("\n", "<br>")}</div>")
                }
            }
            e.attachments.forEach { name ->
                val f = attachmentFile(c, name)
                if (f.exists()) {
                    val b64 = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                    sb.append("<img alt=\"attached photo\" src=\"data:image/jpeg;base64,$b64\">")
                }
            }
            sb.append("</div>")
        }
        sb.append("</body></html>")
        return sb.toString()
    }
}

/** The diary: a list of entries (with a find-by-date field), a new or edited entry with templates, a date and photos, and a download. */
@Composable
fun DiaryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<DiaryEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DiaryEntry?>(null) }
    var creating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var filterDay by remember { mutableStateOf<Long?>(null) }
    var showFind by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        entries = withContext(Dispatchers.IO) { DiaryStore.load(context) }
        loaded = true
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                status = try {
                    val html = withContext(Dispatchers.IO) { DiaryStore.exportHtml(context, entries) }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)!!.use { it.write(html.toByteArray()) }
                    }
                    "Diary saved. Open it in a browser, or print it to PDF."
                } catch (e: Exception) {
                    "Could not save the diary: ${e.message}"
                }
            }
        }
    }

    val open = editing
    if (creating || open != null) {
        DiaryEditor(
            initial = open,
            onSave = { saved ->
                val updated = entries.filter { it.id != saved.id } + saved
                entries = sortDiary(updated)
                scope.launch(Dispatchers.IO) { DiaryStore.saveAll(context, entries) }
                editing = null
                creating = false
            },
            onDelete = {
                open?.let { DiaryStore.delete(context, it) }
                entries = entries.filter { it.id != open?.id }
                scope.launch(Dispatchers.IO) { DiaryStore.saveAll(context, entries) }
                editing = null
                creating = false
            },
            onBack = { editing = null; creating = false }
        )
        return
    }

    val day = filterDay
    val shown = if (day == null) entries else entries.filter { it.entryDate == day }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("My diary", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Write when you want. Add photos. Download it any time.", fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { creating = true }) { Text("New entry") }
            OutlinedButton(
                enabled = entries.isNotEmpty(),
                onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                    exportLauncher.launch("focuslens_diary_$stamp.html")
                }
            ) { Text("Download diary") }
        }
        status?.let { Spacer(Modifier.height(6.dp)); Text(it, fontSize = 12.sp, color = Color.Gray) }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showFind = true }) {
                Text(day?.let { "Date: ${diaryDayLabel(it)}" } ?: "Find by date")
            }
            if (day != null) TextButton(onClick = { filterDay = null }) { Text("Show all") }
        }
        if (showFind) {
            DatePickerDialogPicker(
                initial = day ?: localDayUtc(System.currentTimeMillis()),
                onPicked = { filterDay = it; showFind = false },
                onDismiss = { showFind = false }
            )
        }

        Spacer(Modifier.height(16.dp))
        if (!loaded) {
            Text("Loading…", color = Color.Gray)
        } else if (entries.isEmpty()) {
            Text("No entries yet. Tap New entry to start.", color = Color.Gray)
        } else if (shown.isEmpty()) {
            Text("No entries for this day. Tap Show all to see everything.", color = Color.Gray)
        } else {
            Text("Tap an entry to open and edit it.", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(4.dp))
            shown.forEach { e ->
                val template = DIARY_TEMPLATES.firstOrNull { it.id == e.templateId }?.name ?: "Entry"
                Card(
                    onClick = { editing = e },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(e.title.ifBlank { template }, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text(
                            "${diaryDayLabel(e.entryDate)} · $template" +
                                if (e.attachments.isNotEmpty()) " · ${e.attachments.size} photo(s)" else "",
                            fontSize = 12.sp, color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiaryEditor(
    initial: DiaryEntry?,
    onSave: (DiaryEntry) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var templateId by remember { mutableStateOf(initial?.templateId ?: "daily") }
    val template = DIARY_TEMPLATES.firstOrNull { it.id == templateId } ?: DIARY_TEMPLATES.first()
    var title by remember { mutableStateOf(initial?.title ?: "") }
    // A saved entry keeps its own prompts and answers, so it reopens exactly as it was written.
    val prompts = remember(templateId) {
        mutableStateListOf<String>().apply {
            if (initial != null && initial.sections.isNotEmpty()) addAll(initial.sections.map { it.first })
            else addAll(template.prompts)
        }
    }
    val texts = remember(templateId) {
        mutableStateListOf<String>().apply {
            if (initial != null && initial.sections.isNotEmpty()) addAll(initial.sections.map { it.second })
            else repeat(template.prompts.size) { add("") }
        }
    }
    var entryDate by remember { mutableStateOf(initial?.entryDate ?: localDayUtc(System.currentTimeMillis())) }
    var showDatePicker by remember { mutableStateOf(false) }
    val attachments = remember { mutableStateListOf<String>().apply { addAll(initial?.attachments ?: emptyList()) } }
    var confirmDelete by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val added = withContext(Dispatchers.IO) { uris.mapNotNull { DiaryStore.addAttachment(context, it) } }
                attachments.addAll(added)
                note = if (added.size < uris.size) "Some photos could not be added." else null
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text(if (initial == null) "New entry" else "Edit entry", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (initial == null) {
            Text("Template (optional, you can write anything)", fontSize = 12.sp, color = Color.Gray)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DIARY_TEMPLATES.forEach { t ->
                    FilterChip(selected = templateId == t.id, onClick = { templateId = t.id }, label = { Text(t.name) })
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Text("Entry date", fontSize = 12.sp, color = Color.Gray)
        OutlinedButton(onClick = { showDatePicker = true }) { Text(diaryDayLabel(entryDate)) }
        if (showDatePicker) {
            DatePickerDialogPicker(
                initial = entryDate,
                onPicked = { entryDate = it; showDatePicker = false },
                onDismiss = { showDatePicker = false }
            )
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = title, onValueChange = { title = it },
            label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        prompts.indices.forEach { i ->
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = texts[i], onValueChange = { texts[i] = it },
                label = { Text(prompts[i]) }, modifier = Modifier.fillMaxWidth(), minLines = 3
            )
        }

        Spacer(Modifier.height(12.dp))
        Text("Photos", fontWeight = FontWeight.Medium)
        OutlinedButton(onClick = { pickPhotos.launch("image/*") }) { Text("Add photos") }
        note?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            attachments.forEach { name ->
                val file = DiaryStore.attachmentFile(context, name)
                val bitmap = remember(name) { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }
                if (bitmap != null) {
                    Image(bitmap = bitmap, contentDescription = "Attached photo", modifier = Modifier.size(80.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                val now = System.currentTimeMillis()
                onSave(
                    DiaryEntry(
                        id = initial?.id ?: now,
                        createdAt = initial?.createdAt ?: now,
                        entryDate = entryDate,
                        templateId = templateId,
                        title = title.trim(),
                        sections = prompts.indices.map { i -> prompts[i] to texts[i].trim() },
                        attachments = attachments.toList()
                    )
                )
            }) { Text("Save") }
            if (initial != null) {
                if (!confirmDelete) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete entry") }
                } else {
                    Button(onClick = onDelete) { Text("Yes, delete it") }
                }
            }
        }
    }
}
