package com.focuslens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.text.DateFormat
import java.util.Date

private val MED_DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun notificationsAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, "Share with").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

@Composable
fun MedsScreen() {
    val context = LocalContext.current
    val prefs = remember { MedPrefs(context) }
    var tick by remember { mutableIntStateOf(0) }
    val plans = remember(tick) { prefs.plans() }
    val doses = remember(tick) { prefs.dueToday() }
    var hideNames by remember { mutableStateOf(prefs.hideNamesInNotifications()) }
    var includeNotes by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MedPlan?>(null) }
    var showForm by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun afterPlansChanged() {
        MedScheduler.rescheduleAll(context)
        tick++
        if (!notificationsAllowed(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("Medication", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Stays on this phone. Not synced, not uploaded, not in cloud backup.",
                fontSize = 13.sp, color = Color.Gray
            )
            Text(
                "FocusLens never advises doses or products. Follow your doctor or pharmacist.",
                fontSize = 13.sp, color = Color.Gray
            )
        }

        item { Text("Today", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        if (doses.isEmpty()) {
            item { Text("Nothing scheduled today.", color = Color.Gray) }
        }
        items(doses, key = { it.key }) { dose ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            dose.time + "  " + dose.plan.name +
                                if (dose.plan.strengthText.isNotBlank()) " " + dose.plan.strengthText else "",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold
                        )
                        val label = when (dose.state) {
                            DoseState.UPCOMING -> "Upcoming"
                            DoseState.DUE -> "Due now"
                            DoseState.TAKEN -> "Taken"
                            DoseState.MISSED -> "Missed"
                        }
                        Text(
                            label, fontSize = 12.sp,
                            color = if (dose.state == DoseState.MISSED) Color(0xFFB00020) else Color.Gray
                        )
                    }
                    if (dose.state == DoseState.TAKEN) {
                        TextButton(onClick = { prefs.undo(dose.key); tick++ }) { Text("Undo") }
                    } else {
                        Button(onClick = { prefs.markTaken(dose.key); tick++ }) { Text("Taken") }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date())
                    shareText(context, MedLogic.shareSummary(date, doses))
                }) { Text("Share today's update") }
            }
        }

        item { Text("My medications", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        if (plans.isEmpty()) {
            item { Text("None added yet.", color = Color.Gray) }
        }
        items(plans, key = { it.id }) { plan ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        plan.name + if (plan.strengthText.isNotBlank()) " " + plan.strengthText else "",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold
                    )
                    if (plan.instructions.isNotBlank()) {
                        Text(plan.instructions, fontSize = 13.sp, color = Color.Gray)
                    }
                    val days = if (plan.days.isEmpty()) "Every day"
                    else plan.days.sorted().joinToString(" ") { MED_DAY_LABELS[it - 1] }
                    Text(plan.times.sorted().joinToString(", ") + "  |  " + days, fontSize = 12.sp, color = Color.Gray)
                    Row {
                        TextButton(onClick = { editing = plan; showForm = true }) { Text("Edit") }
                        TextButton(onClick = {
                            prefs.delete(plan.id)
                            afterPlansChanged()
                        }) { Text("Delete") }
                    }
                }
            }
        }
        item {
            Button(onClick = { editing = null; showForm = true }) { Text("Add medication") }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("Share with family", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "Nothing is sent by FocusLens. You choose the app and the person.",
                fontSize = 12.sp, color = Color.Gray
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeNotes, onCheckedChange = { includeNotes = it })
                Text("Include notes")
            }
            OutlinedButton(onClick = { shareText(context, MedLogic.listSummary(plans, includeNotes)) }) {
                Text("Share my medication list")
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Hide medication name in notifications")
                    Text(
                        "On: reminders say \"Time for a medication\", good for the lock screen.",
                        fontSize = 12.sp, color = Color.Gray
                    )
                }
                Switch(checked = hideNames, onCheckedChange = {
                    hideNames = it
                    prefs.setHideNamesInNotifications(it)
                })
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    if (showForm) {
        MedPlanDialog(
            initial = editing,
            onSave = { plan ->
                prefs.upsert(plan)
                showForm = false
                afterPlansChanged()
            },
            onDismiss = { showForm = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedPlanDialog(initial: MedPlan?, onSave: (MedPlan) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var strength by remember { mutableStateOf(initial?.strengthText ?: "") }
    var instructions by remember { mutableStateOf(initial?.instructions ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    val times = remember { mutableStateListOf<String>().apply { addAll(initial?.times ?: emptyList()) } }
    val days = remember { mutableStateListOf<Int>().apply { addAll(initial?.days ?: emptySet()) } }
    var timeInput by remember { mutableStateOf("") }
    var timeError by remember { mutableStateOf(false) }
    var scanStatus by remember { mutableStateOf<String?>(null) }

    // Thumbnail only; big label text is enough for OCR. The bitmap is never saved anywhere.
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
        if (bmp == null) {
            scanStatus = "No photo taken."
        } else {
            scanStatus = "Reading label..."
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { result ->
                    val lines = result.textBlocks.flatMap { it.lines }
                        .map { it.text to (it.boundingBox?.height() ?: 0) }
                    val guess = MedLogic.parseLabel(lines)
                    if (guess.name.isNotBlank()) name = guess.name
                    if (guess.strength.isNotBlank()) strength = guess.strength
                    if (guess.instructions.isNotBlank()) instructions = guess.instructions
                    scanStatus = if (lines.isEmpty()) "No text found. Try again or type it in."
                    else "Review the suggestions below. Text recognition can misread: check every field against the box."
                    recognizer.close()
                }
                .addOnFailureListener {
                    scanStatus = "Could not read the label. Please type it in."
                    recognizer.close()
                }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add medication" else "Edit medication") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Type exactly what is printed on your label. FocusLens never suggests doses or products.",
                    fontSize = 12.sp, color = Color.Gray
                )
                OutlinedButton(onClick = { camera.launch(null) }) { Text("Scan label") }
                scanStatus?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name (as on the label)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = strength, onValueChange = { strength = it },
                    label = { Text("Strength (as printed, e.g. 500 mg)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = instructions, onValueChange = { instructions = it },
                    label = { Text("Label instructions (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Quick times (tap to add)", fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Morning" to "08:00", "Afternoon" to "13:00", "Evening" to "20:00").forEach { (label, t) ->
                        AssistChip(
                            onClick = { if (t !in times) times.add(t) },
                            label = { Text("$label $t") }
                        )
                    }
                }
                Text("Times (24-hour HH:mm)", fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    times.sorted().forEach { t ->
                        AssistChip(onClick = { times.remove(t) }, label = { Text("$t  x") })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = timeInput,
                        onValueChange = { timeInput = it; timeError = false },
                        label = { Text("e.g. 08:00") }, singleLine = true, isError = timeError,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = {
                        val n = MedLogic.normalizeTime(timeInput)
                        if (n == null) timeError = true
                        else {
                            if (!times.contains(n)) times.add(n)
                            timeInput = ""
                        }
                    }) { Text("Add") }
                }
                Text("Days (none selected = every day)", fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MED_DAY_LABELS.forEachIndexed { idx, label ->
                        val iso = idx + 1
                        FilterChip(
                            selected = days.contains(iso),
                            onClick = { if (days.contains(iso)) days.remove(iso) else days.add(iso) },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes (private)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        MedPlan(
                            id = initial?.id ?: MedPrefs(context).newId(),
                            name = name.trim(),
                            strengthText = strength.trim(),
                            instructions = instructions.trim(),
                            times = times.sorted(),
                            days = days.toSet(),
                            notes = notes.trim()
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
