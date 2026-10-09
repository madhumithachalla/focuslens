@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.focuslens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslens.data.AgeBracket
import com.focuslens.data.ProfileEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Not private: SettingsScreen.kt reuses these same option lists and ChecklistRow so editing
// a profile after onboarding shows identical choices, not a second, divergent copy of them.
data class AgeOption(val bracket: AgeBracket, val label: String)

val AGE_OPTIONS = listOf(
    AgeOption(AgeBracket.UNDER_18, "Under 18"),
    AgeOption(AgeBracket.AGE_18_24, "18–24"),
    AgeOption(AgeBracket.AGE_25_34, "25–34"),
    AgeOption(AgeBracket.AGE_35_44, "35–44"),
    AgeOption(AgeBracket.AGE_45_PLUS, "45+"),
    AgeOption(AgeBracket.PREFER_NOT_TO_SAY, "Prefer not to say")
)

/** A dropdown with a real escape hatch — never force someone into a fixed gender list. */
const val SELF_DESCRIBE = "Prefer to self-describe"
val GENDER_OPTIONS = listOf("Woman", "Man", "Non-binary", SELF_DESCRIBE, "Prefer not to say")

/** Free-form life-context tags, not a fixed identity — used later to tailor journal prompts and suggestions. */
val TOPIC_OPTIONS = listOf(
    "student" to "Student",
    "working" to "Working professional",
    "parent" to "Parent / caregiver",
    "shift_worker" to "Shift worker",
    "creative" to "Creative / freelancer",
    "fitness" to "Fitness-focused",
    "mental_health" to "Mental health-focused",
    "reading" to "Reading",
    "writing" to "Writing",
    "other_activities" to "Other learning/creative activities"
)

/**
 * Why someone's actually here — read later by Learn/Guide to lead with the right section
 * (e.g. doom-scrolling → Focus tips first) instead of showing everything with equal weight.
 * "Prefer not to say" is its own exclusive option, same pattern as the cycle-tracking skip.
 */
val MOTIVATION_OPTIONS = listOf(
    "reduce_scrolling" to "Reduce doom-scrolling / social media use",
    "focus" to "Improve focus & productivity",
    "cycle_tracking" to "Track my cycle or energy",
    "mental_health" to "Mental health support",
    "habits" to "Build better habits/routines",
    "other" to "Something else"
)

/**
 * Entirely optional, never required to continue, never used to gate any feature — only to
 * more readily surface relevant Learn resources (ADHD/neurodivergence screeners, anxiety/
 * depression screeners — see MentalHealthScreen.kt). A free-form list, not a diagnosis:
 * anyone can pick whatever applies, self-identified or diagnosed, or skip entirely. STRICT
 * RULE: this section never gates, limits, or changes which features are usable — only which
 * Learn resources get surfaced more readily. "Prefer not to say" and "Not sure" are their own
 * exclusive choices (not silence) — picking either clears every checkbox below, same pattern
 * as elsewhere in this screen; "Not sure" additionally flags MentalHealthScreen to offer real
 * screeners rather than a diagnosis of its own.
 */
val NEURODIVERGENT_OPTIONS = listOf(
    "adhd" to "ADHD / ADD",
    "autism" to "Autism",
    "dyslexia" to "Dyslexia",
    "dyspraxia" to "Dyspraxia",
    "ocd" to "OCD",
    "tics" to "Tourette's / tic disorder",
    "anxiety" to "Anxiety",
    "depression" to "Depression",
    "other_health_condition" to "Another health condition"
)

/**
 * One-time setup. Age and gender are dropdowns (single-choice, so a dropdown fits better than
 * a long list of rows); gender keeps a self-describe text fallback so the dropdown never
 * becomes a forced fixed list. Cycle tracking and topics stay checklists since both are
 * genuinely multi-select. Continue is a fixed footer; a visible scroll thumb on the right
 * edge makes it clear there's more content below. No real account behind "name" — it's
 * stored locally like the rest of this profile, same privacy pitch as everywhere else.
 */
@Composable
fun OnboardingScreen(onDone: (ProfileEntity) -> Unit) {
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf<AgeBracket?>(null) }
    var ageMenuOpen by remember { mutableStateOf(false) }
    var gender by remember { mutableStateOf<String?>(null) }
    var genderMenuOpen by remember { mutableStateOf(false) }
    var genderCustom by remember { mutableStateOf("") }
    var trackMenstrual by remember { mutableStateOf(false) }
    var trackGeneral by remember { mutableStateOf(false) }
    var skipCycle by remember { mutableStateOf(false) }
    var selectedTopics by remember { mutableStateOf(setOf<String>()) }
    var selectedNeurodivergent by remember { mutableStateOf(setOf<String>()) }
    var neurodivergentCustom by remember { mutableStateOf("") }
    var neurodivergentPreferNotToSay by remember { mutableStateOf(false) }
    var neurodivergentNotSure by remember { mutableStateOf(false) }
    var selectedMotivations by remember { mutableStateOf(setOf<String>()) }
    var motivationPreferNotToSay by remember { mutableStateOf(false) }
    var birthday by remember { mutableStateOf<String?>(null) } // yyyyMMdd
    var birthdayPicking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun toggleMotivation(id: String) {
        selectedMotivations = if (id in selectedMotivations) selectedMotivations - id else selectedMotivations + id
        if (selectedMotivations.isNotEmpty()) motivationPreferNotToSay = false
    }
    fun toggleMotivationPreferNotToSay() {
        motivationPreferNotToSay = !motivationPreferNotToSay
        if (motivationPreferNotToSay) selectedMotivations = emptySet()
    }

    fun toggleNeurodivergent(id: String) {
        selectedNeurodivergent = if (id in selectedNeurodivergent) selectedNeurodivergent - id else selectedNeurodivergent + id
        if (selectedNeurodivergent.isNotEmpty()) {
            neurodivergentPreferNotToSay = false
            neurodivergentNotSure = false
        }
    }
    fun toggleNeurodivergentPreferNotToSay() {
        neurodivergentPreferNotToSay = !neurodivergentPreferNotToSay
        if (neurodivergentPreferNotToSay) {
            selectedNeurodivergent = emptySet()
            neurodivergentCustom = ""
            neurodivergentNotSure = false
        }
    }
    fun toggleNeurodivergentNotSure() {
        neurodivergentNotSure = !neurodivergentNotSure
        if (neurodivergentNotSure) {
            selectedNeurodivergent = emptySet()
            neurodivergentCustom = ""
            neurodivergentPreferNotToSay = false
        }
    }

    fun toggleMenstrual() {
        trackMenstrual = !trackMenstrual
        if (trackMenstrual) skipCycle = false
    }
    fun toggleGeneral() {
        trackGeneral = !trackGeneral
        if (trackGeneral) skipCycle = false
    }
    fun toggleSkipCycle() {
        skipCycle = !skipCycle
        if (skipCycle) { trackMenstrual = false; trackGeneral = false }
    }

    val cycleChoiceMade = trackMenstrual || trackGeneral || skipCycle
    val canContinue = name.isNotBlank() && age != null && cycleChoiceMade

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    Spacer(Modifier.height(32.dp))
                    Text("Quick setup", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Just enough to set sensible defaults — everything here is editable later in Settings.",
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE8EFEA), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("🔒", fontSize = 15.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Private by default: everything you enter here stays on this device. It's never used to train any model, and never shared anywhere without your permission.",
                            fontSize = 11.5.sp,
                            color = Color(0xFF3A5245)
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Text("What should we call you?", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Name or nickname") },
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
                            placeholder = { Text("Select your age range") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ageMenuOpen) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = ageMenuOpen, onDismissRequest = { ageMenuOpen = false }) {
                            AGE_OPTIONS.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.label) },
                                    onClick = { age = opt.bracket; ageMenuOpen = false }
                                )
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
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = { gender = opt; genderMenuOpen = false }
                                )
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
                    Spacer(Modifier.height(2.dp))
                    Text("Only used so FocusLens can remind you about your own birthday checklist (see Birthdays).", fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = birthday?.let(::formatYyyyMmDd) ?: "",
                        onValueChange = {},
                        readOnly = true,
                        placeholder = { Text("Select your birthday") },
                        trailingIcon = {
                            TextButton(onClick = { birthdayPicking = true }) { Text("Pick") }
                        },
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
                    Text("Why are you using FocusLens? (optional)", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text("Pick as many as apply — helps Guide and Learn lead with what's relevant to you.", fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                }

                items(MOTIVATION_OPTIONS) { (id, label) ->
                    ChecklistRow(label = label, checked = id in selectedMotivations, onToggle = { toggleMotivation(id) })
                }

                item {
                    ChecklistRow("Prefer not to say", motivationPreferNotToSay, { toggleMotivationPreferNotToSay() })

                    Spacer(Modifier.height(20.dp))
                    Text("Tracking", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "If you get periods, pick that directly below. Either way, you can also turn on the other " +
                            "trackers (energy, mood, sleep, water) — pick as many as apply, none are exclusive.",
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    ChecklistRow("I get periods — track my cycle", trackMenstrual, { toggleMenstrual() })
                    ChecklistRow("Show other trackers (energy, mood, sleep, water)", trackGeneral, { toggleGeneral() })
                    ChecklistRow("Skip for now", skipCycle, { toggleSkipCycle() })

                    Spacer(Modifier.height(20.dp))
                    Text("Topics you're part of (optional)", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text("Pick any that apply — helps tailor journal prompts and suggestions later.", fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                }

                items(TOPIC_OPTIONS) { (id, label) ->
                    ChecklistRow(
                        label = label,
                        checked = id in selectedTopics,
                        onToggle = {
                            selectedTopics = if (id in selectedTopics) selectedTopics - id else selectedTopics + id
                        }
                    )
                }

                item {
                    Spacer(Modifier.height(20.dp))
                    Text("Disability, neurodivergence & mental health (optional)", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Completely optional, skippable, and never used to limit anything in the app — strictly: " +
                            "nothing below ever gates or changes which features you can use. It only lets us more " +
                            "readily point you toward relevant resources in Learn, since this app already builds in " +
                            "things that can help (structured to-dos, gentle reminders, focus tools). Self-identified " +
                            "or diagnosed, whatever applies to you — and \"Not sure\" is a real answer, not a gap: " +
                            "picking it offers real screening resources, never a diagnosis of our own.",
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                }

                items(NEURODIVERGENT_OPTIONS) { (id, label) ->
                    ChecklistRow(label = label, checked = id in selectedNeurodivergent, onToggle = { toggleNeurodivergent(id) })
                }

                item {
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
                    ChecklistRow("Not sure", neurodivergentNotSure, { toggleNeurodivergentNotSure() })
                    ChecklistRow("Prefer not to say", neurodivergentPreferNotToSay, { toggleNeurodivergentPreferNotToSay() })
                }

                item { Spacer(Modifier.height(16.dp)) }
            }

            ScrollThumb(listState, Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
        }

        // Fixed footer — always visible, never requires scrolling to find.
        Surface(shadowElevation = 8.dp) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                if (!canContinue) {
                    Text(
                        "Add a name, age range and a tracking option to continue.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Button(
                    enabled = canContinue,
                    onClick = {
                        val finalGender = when {
                            gender == SELF_DESCRIBE -> genderCustom.ifBlank { null }
                            gender == "Prefer not to say" -> null
                            else -> gender
                        }
                        val neurodivergentAll = selectedNeurodivergent +
                            listOfNotNull(neurodivergentCustom.trim().ifBlank { null }) +
                            listOfNotNull(
                                "not_sure".takeIf { neurodivergentNotSure },
                                "prefer_not_to_say".takeIf { neurodivergentPreferNotToSay }
                            )
                        onDone(
                            ProfileEntity(
                                preferredName = name.trim(),
                                ageBracket = age!!,
                                genderIdentity = finalGender,
                                trackMenstrualCycle = trackMenstrual,
                                trackGeneralEnergy = trackGeneral,
                                topics = selectedTopics.joinToString(","),
                                neurodivergentTags = neurodivergentAll.joinToString(","),
                                usageMotivation = selectedMotivations.joinToString(","),
                                birthday = birthday,
                                onboardedAt = System.currentTimeMillis()
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue")
                }
            }
        }
    }
}

fun formatYyyyMmDd(date: String): String {
    if (date.length != 8) return date
    val month = date.substring(4, 6).toInt()
    val day = date.substring(6, 8).toInt()
    val names = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    return "${names[month - 1]} $day"
}

/** A minimal always-visible scroll indicator — shows there's more content without needing a swipe to discover it. */
@Composable
private fun ScrollThumb(state: androidx.compose.foundation.lazy.LazyListState, modifier: Modifier = Modifier) {
    val info = state.layoutInfo
    val total = info.totalItemsCount
    val visible = info.visibleItemsInfo.size
    if (total == 0 || visible >= total) return

    BoxWithConstraints(modifier.width(5.dp)) {
        val trackHeightPx = constraints.maxHeight.toFloat()
        val thumbFraction = (visible.toFloat() / total).coerceIn(0.08f, 1f)
        val maxScroll = (total - visible).coerceAtLeast(1)
        val scrollFraction = (state.firstVisibleItemIndex.toFloat() / maxScroll).coerceIn(0f, 1f)
        val thumbHeightDp = with(androidx.compose.ui.platform.LocalDensity.current) { (trackHeightPx * thumbFraction).toDp() }
        val offsetDp = with(androidx.compose.ui.platform.LocalDensity.current) {
            ((trackHeightPx - trackHeightPx * thumbFraction) * scrollFraction).toDp()
        }
        Box(
            Modifier
                .offset(y = offsetDp)
                .height(thumbHeightDp)
                .fillMaxWidth()
                .background(Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
        )
    }
}

@Composable
fun ChecklistRow(label: String, checked: Boolean, onToggle: () -> Unit, radio: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (radio) {
            RadioButton(selected = checked, onClick = onToggle)
        } else {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        }
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 15.sp)
    }
}
