@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.focuslens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

private data class AgeOption(val bracket: AgeBracket, val label: String)

private val AGE_OPTIONS = listOf(
    AgeOption(AgeBracket.UNDER_18, "Under 18"),
    AgeOption(AgeBracket.AGE_18_24, "18–24"),
    AgeOption(AgeBracket.AGE_25_34, "25–34"),
    AgeOption(AgeBracket.AGE_35_44, "35–44"),
    AgeOption(AgeBracket.AGE_45_PLUS, "45+"),
    AgeOption(AgeBracket.PREFER_NOT_TO_SAY, "Prefer not to say")
)

/** Free-form life-context tags, not a fixed identity — used later to tailor journal prompts and suggestions. */
private val TOPIC_OPTIONS = listOf(
    "student" to "Student",
    "working" to "Working professional",
    "parent" to "Parent / caregiver",
    "shift_worker" to "Shift worker",
    "creative" to "Creative / freelancer",
    "fitness" to "Fitness-focused",
    "mental_health" to "Mental health-focused"
)

/**
 * One-time setup. Age is a single-choice dropdown (six rows of radio buttons wasted a full
 * screen of vertical space for one choice); cycle tracking and topics stay genuine checklists
 * since both are multi-valued. Continue is a fixed footer, not buried at the end of the
 * scroll. Nothing here gates a feature — it only seeds Settings defaults a person can change
 * immediately after.
 */
@Composable
fun OnboardingScreen(onDone: (ProfileEntity) -> Unit) {
    var age by remember { mutableStateOf<AgeBracket?>(null) }
    var ageMenuOpen by remember { mutableStateOf(false) }
    var gender by remember { mutableStateOf("") }
    var trackMenstrual by remember { mutableStateOf(false) }
    var trackGeneral by remember { mutableStateOf(false) }
    var skipCycle by remember { mutableStateOf(false) }
    var selectedTopics by remember { mutableStateOf(setOf<String>()) }

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

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 24.dp),
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
                        .background(Color(0xFFE8EFEA), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
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
                Text("Gender (optional, your own words)", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = gender,
                    onValueChange = { gender = it },
                    placeholder = { Text("e.g. woman, non-binary, leave blank") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(20.dp))
                Text("Cycle & energy tracking", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Pick as many as apply — tracking a cycle and general energy aren't mutually exclusive.",
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                ChecklistRow("Menstrual cycle", trackMenstrual, { toggleMenstrual() })
                ChecklistRow("General energy", trackGeneral, { toggleGeneral() })
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

            item { Spacer(Modifier.height(16.dp)) }
        }

        // Fixed footer — always visible, never requires scrolling to find.
        Surface(shadowElevation = 8.dp) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                if (!(age != null && cycleChoiceMade)) {
                    Text(
                        "Pick an age range and a cycle-tracking option to continue.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Button(
                    enabled = age != null && cycleChoiceMade,
                    onClick = {
                        onDone(
                            ProfileEntity(
                                ageBracket = age!!,
                                genderIdentity = gender.ifBlank { null },
                                trackMenstrualCycle = trackMenstrual,
                                trackGeneralEnergy = trackGeneral,
                                topics = selectedTopics.joinToString(","),
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

@Composable
private fun ChecklistRow(label: String, checked: Boolean, onToggle: () -> Unit, radio: Boolean = false) {
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
