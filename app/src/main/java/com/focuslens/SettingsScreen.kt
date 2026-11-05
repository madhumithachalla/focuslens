@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.focuslens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslens.data.AgeBracket
import com.focuslens.data.AppDatabase
import com.focuslens.data.ProfileEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything from Quick setup, editable after the fact — onboarding's own copy promises
 * "editable later in Settings," so this is that screen. Reuses the exact same option lists
 * and ChecklistRow as OnboardingScreen.kt (made non-private there) so editing never shows a
 * second, drifted copy of the same choices.
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()

    var profile by remember { mutableStateOf<ProfileEntity?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var syncOn by remember { mutableStateOf(UsageSync.isEnabled(context)) }
    val signedIn = remember { UsageSync.isSignedIn() }
    var syncBusy by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }

    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf<AgeBracket?>(null) }
    var ageMenuOpen by remember { mutableStateOf(false) }
    var gender by remember { mutableStateOf<String?>(null) }
    var genderMenuOpen by remember { mutableStateOf(false) }
    var genderCustom by remember { mutableStateOf("") }
    var birthday by remember { mutableStateOf<String?>(null) }
    var birthdayPicking by remember { mutableStateOf(false) }
    var trackMenstrual by remember { mutableStateOf(false) }
    var trackGeneral by remember { mutableStateOf(false) }
    var selectedTopics by remember { mutableStateOf(setOf<String>()) }
    var selectedMotivations by remember { mutableStateOf(setOf<String>()) }
    var motivationPreferNotToSay by remember { mutableStateOf(false) }
    var selectedNeurodivergent by remember { mutableStateOf(setOf<String>()) }
    var neurodivergentCustom by remember { mutableStateOf("") }
    var neurodivergentNotSure by remember { mutableStateOf(false) }
    var neurodivergentPreferNotToSay by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val p = db.profileDao().get()
        profile = p
        if (p != null) {
            name = p.preferredName
            age = p.ageBracket
            gender = when {
                p.genderIdentity == null -> null
                p.genderIdentity in GENDER_OPTIONS -> p.genderIdentity
                else -> SELF_DESCRIBE
            }
            if (gender == SELF_DESCRIBE) genderCustom = p.genderIdentity ?: ""
            birthday = p.birthday
            trackMenstrual = p.trackMenstrualCycle
            trackGeneral = p.trackGeneralEnergy
            selectedTopics = p.topics.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
            selectedMotivations = p.usageMotivation.split(",").map { it.trim() }
                .filter { it.isNotBlank() && it in MOTIVATION_OPTIONS.map { m -> m.first } }.toSet()
            val healthTags = p.neurodivergentTags.split(",").map { it.trim() }.filter { it.isNotBlank() }
            neurodivergentNotSure = "not_sure" in healthTags
            neurodivergentPreferNotToSay = "prefer_not_to_say" in healthTags
            val knownIds = NEURODIVERGENT_OPTIONS.map { it.first }
            selectedNeurodivergent = healthTags.filter { it in knownIds }.toSet()
            neurodivergentCustom = healthTags.firstOrNull { it !in knownIds && it != "not_sure" && it != "prefer_not_to_say" } ?: ""
        }
        loaded = true
    }

    fun toggleSetItem(set: Set<String>, id: String): Set<String> = if (id in set) set - id else set + id

    fun save() {
        val current = profile ?: return
        val finalGender = when {
            gender == SELF_DESCRIBE -> genderCustom.trim().ifBlank { null }
            gender == null || gender == "Prefer not to say" -> null
            else -> gender
        }
        val neurodivergentAll = selectedNeurodivergent +
            listOfNotNull(neurodivergentCustom.trim().ifBlank { null }) +
            listOfNotNull(
                "not_sure".takeIf { neurodivergentNotSure },
                "prefer_not_to_say".takeIf { neurodivergentPreferNotToSay }
            )
        val updated = current.copy(
            preferredName = name.trim().ifBlank { current.preferredName },
            ageBracket = age ?: current.ageBracket,
            genderIdentity = finalGender,
            birthday = birthday,
            trackMenstrualCycle = trackMenstrual,
            trackGeneralEnergy = trackGeneral,
            topics = selectedTopics.joinToString(","),
            usageMotivation = if (motivationPreferNotToSay) "" else selectedMotivations.joinToString(","),
            neurodivergentTags = neurodivergentAll.joinToString(",")
        )
        scope.launch {
            db.profileDao().update(updated)
            profile = updated
            saved = true
        }
    }

    if (!loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (profile == null) {
        Box(Modifier.fillMaxSize().padding(24.dp)) {
            Text("No profile found yet — complete setup first.", fontSize = 14.sp, color = Color.Gray)
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text("Settings", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("Everything from setup, editable any time — nothing here leaves this device unless you turn on screen-time sync below.", fontSize = 13.sp, color = Color.Gray)

            Spacer(Modifier.height(16.dp))
            UpdateCard()

            Spacer(Modifier.height(20.dp))
            Text("What should we call you?", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))
            Text("Age range", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(expanded = ageMenuOpen, onExpandedChange = { ageMenuOpen = it }) {
                OutlinedTextField(
                    value = AGE_OPTIONS.firstOrNull { it.bracket == age }?.label ?: "",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ageMenuOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = ageMenuOpen, onDismissRequest = { ageMenuOpen = false }) {
                    AGE_OPTIONS.forEach { opt ->
                        DropdownMenuItem(text = { Text(opt.label) }, onClick = { age = opt.bracket; ageMenuOpen = false })
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Gender (optional)", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(expanded = genderMenuOpen, onExpandedChange = { genderMenuOpen = it }) {
                OutlinedTextField(
                    value = gender ?: "",
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("Select, or self-describe") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderMenuOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = genderMenuOpen, onDismissRequest = { genderMenuOpen = false }) {
                    GENDER_OPTIONS.forEach { opt ->
                        DropdownMenuItem(text = { Text(opt) }, onClick = { gender = opt; genderMenuOpen = false })
                    }
                }
            }
            if (gender == SELF_DESCRIBE) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = genderCustom,
                    onValueChange = { genderCustom = it },
                    placeholder = { Text("In your own words") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("Birthday (optional)", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = birthday?.let(::formatYyyyMmDd) ?: "",
                onValueChange = {},
                readOnly = true,
                placeholder = { Text("Select your birthday") },
                trailingIcon = { TextButton(onClick = { birthdayPicking = true }) { Text("Pick") } },
                modifier = Modifier.fillMaxWidth().clickable { birthdayPicking = true }
            )
            if (birthdayPicking) {
                DatePickerDialogPicker(
                    initial = null,
                    onPicked = { millis ->
                        birthday = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(millis))
                        birthdayPicking = false
                    },
                    onDismiss = { birthdayPicking = false }
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("Tracking", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            ChecklistRow("I get periods — track my cycle", trackMenstrual, { trackMenstrual = !trackMenstrual })
            ChecklistRow("Show other trackers (energy, mood, sleep, water)", trackGeneral, { trackGeneral = !trackGeneral })

            Spacer(Modifier.height(20.dp))
            Text("Topics you're part of", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            TOPIC_OPTIONS.forEach { (id, label) ->
                ChecklistRow(label, id in selectedTopics, { selectedTopics = toggleSetItem(selectedTopics, id) })
            }

            Spacer(Modifier.height(20.dp))
            Text("Why are you using FocusLens?", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            MOTIVATION_OPTIONS.forEach { (id, label) ->
                ChecklistRow(label, id in selectedMotivations, {
                    selectedMotivations = toggleSetItem(selectedMotivations, id)
                    if (selectedMotivations.isNotEmpty()) motivationPreferNotToSay = false
                })
            }
            ChecklistRow("Prefer not to say", motivationPreferNotToSay, {
                motivationPreferNotToSay = !motivationPreferNotToSay
                if (motivationPreferNotToSay) selectedMotivations = emptySet()
            })

            Spacer(Modifier.height(20.dp))
            Text("Disability, neurodivergence & mental health", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text("Strictly: nothing here ever gates or changes which features you can use.", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            NEURODIVERGENT_OPTIONS.forEach { (id, label) ->
                ChecklistRow(label, id in selectedNeurodivergent, {
                    selectedNeurodivergent = toggleSetItem(selectedNeurodivergent, id)
                    if (selectedNeurodivergent.isNotEmpty()) {
                        neurodivergentPreferNotToSay = false
                        neurodivergentNotSure = false
                    }
                })
            }
            OutlinedTextField(
                value = neurodivergentCustom,
                onValueChange = {
                    neurodivergentCustom = it
                    if (it.isNotBlank()) { neurodivergentPreferNotToSay = false; neurodivergentNotSure = false }
                },
                placeholder = { Text("Something else (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            ChecklistRow("Not sure", neurodivergentNotSure, {
                neurodivergentNotSure = !neurodivergentNotSure
                if (neurodivergentNotSure) {
                    selectedNeurodivergent = emptySet(); neurodivergentCustom = ""; neurodivergentPreferNotToSay = false
                }
            })
            ChecklistRow("Prefer not to say", neurodivergentPreferNotToSay, {
                neurodivergentPreferNotToSay = !neurodivergentPreferNotToSay
                if (neurodivergentPreferNotToSay) {
                    selectedNeurodivergent = emptySet(); neurodivergentCustom = ""; neurodivergentNotSure = false
                }
            })

            Spacer(Modifier.height(24.dp))
            Text("Sync screen time to my account", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Off by default. When on, the last 7 days of per-day screen-time totals, app names and an hourly " +
                    "breakdown are uploaded to your account so you can see them on the web. Only you can read them. " +
                    "Turning it off stops future uploads; use the button below to remove what is already uploaded.",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            if (!signedIn) {
                Text("Sign in on the Account screen to use this.", fontSize = 12.sp, color = Color(0xFFB26A00))
                Spacer(Modifier.height(4.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Sync screen time to my account", fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = syncOn && signedIn,
                    enabled = signedIn,
                    onCheckedChange = { on ->
                        syncOn = on
                        UsageSync.setEnabled(context, on)
                        syncMessage = null
                        if (on) {
                            scope.launch {
                                val repo = UsageRepository(context)
                                UsageSync.maybeSync(context, repo, UsageHistoryRepository(context, repo), force = true)
                            }
                        }
                    }
                )
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = {
                    syncBusy = true
                    syncMessage = null
                    scope.launch {
                        val ok = UsageSync.deleteAll()
                        syncBusy = false
                        syncMessage = if (ok) "Deleted your synced usage." else "Couldn't delete right now — check your connection and try again."
                    }
                },
                enabled = signedIn && !syncBusy
            ) { Text("Delete my synced usage") }
            Spacer(Modifier.height(16.dp))
            var personal by remember { mutableStateOf(PrivacyPrefs.personalised(context)) }
            var confirmDelete by remember { mutableStateOf(false) }
            var deleting by remember { mutableStateOf(false) }
            Text("Privacy & your data", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(PrivacyPrefs.CONSENT_TEXT, fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Learn from my data on this phone", fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(checked = personal, onCheckedChange = { on ->
                    personal = on
                    PrivacyPrefs.setPersonalised(context, on)
                })
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { confirmDelete = true }, enabled = !deleting) {
                Text("Delete all my data on this phone")
            }
            if (confirmDelete) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text("Delete all data on this phone?") },
                    text = { Text("This removes your tasks, check-ins, journal, cycle, sleep, screen-time history and settings from this phone. It cannot be undone.") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            confirmDelete = false
                            deleting = true
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                PrivacyPrefs.deleteAllLocalData(context)
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    personal = false
                                    deleting = false
                                    syncMessage = "All data on this phone was deleted."
                                }
                            }
                        }) { Text("Delete") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
                    }
                )
            }
            if (syncMessage != null) {
                Spacer(Modifier.height(4.dp))
                Text(syncMessage ?: "", fontSize = 12.sp, color = Color.Gray)
            }

            Spacer(Modifier.height(24.dp))
        }

        Surface(shadowElevation = 8.dp) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                if (saved) {
                    Text("Saved.", fontSize = 12.sp, color = Color(0xFF2E7D5B))
                    Spacer(Modifier.height(6.dp))
                }
                Button(onClick = { saved = false; save() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Save changes")
                }
            }
        }
    }
}
