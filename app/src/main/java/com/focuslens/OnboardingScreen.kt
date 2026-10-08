package com.focuslens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslens.data.AgeBracket
import com.focuslens.data.CycleMode
import com.focuslens.data.ProfileEntity
import kotlinx.coroutines.launch

private data class AgeOption(val bracket: AgeBracket, val label: String)

private val AGE_OPTIONS = listOf(
    AgeOption(AgeBracket.UNDER_18, "Under 18"),
    AgeOption(AgeBracket.AGE_18_24, "18–24"),
    AgeOption(AgeBracket.AGE_25_34, "25–34"),
    AgeOption(AgeBracket.AGE_35_44, "35–44"),
    AgeOption(AgeBracket.AGE_45_PLUS, "45+"),
    AgeOption(AgeBracket.PREFER_NOT_TO_SAY, "Prefer not to say")
)

/**
 * One-time setup: only asks what actually changes the app's defaults — age bracket (for
 * copy tone) and whether to offer cycle tracking at all, with gender left as free text
 * so nobody is forced into a fixed list. Nothing here gates a feature; it only seeds
 * sensible Settings defaults a person can change immediately afterward.
 */
@Composable
fun OnboardingScreen(onDone: (ProfileEntity) -> Unit) {
    var age by remember { mutableStateOf<AgeBracket?>(null) }
    var gender by remember { mutableStateOf("") }
    var cycleMode by remember { mutableStateOf<CycleMode?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp)) {
        Text("Quick setup", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Just enough to set sensible defaults — everything here is editable later in Settings.",
            fontSize = 14.sp
        )
        Spacer(Modifier.height(24.dp))

        Text("Age range", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(AGE_OPTIONS) { opt ->
                FilterChip(
                    selected = age == opt.bracket,
                    onClick = { age = opt.bracket },
                    label = { Text(opt.label) },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
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
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = cycleMode == CycleMode.MENSTRUAL, onClick = { cycleMode = CycleMode.MENSTRUAL }, label = { Text("Menstrual cycle") })
            FilterChip(selected = cycleMode == CycleMode.GENERAL_ENERGY, onClick = { cycleMode = CycleMode.GENERAL_ENERGY }, label = { Text("General energy") })
            FilterChip(selected = cycleMode == CycleMode.OFF, onClick = { cycleMode = CycleMode.OFF }, label = { Text("Skip") })
        }

        Spacer(Modifier.height(28.dp))
        Button(
            enabled = age != null && cycleMode != null,
            onClick = {
                onDone(
                    ProfileEntity(
                        ageBracket = age!!,
                        genderIdentity = gender.ifBlank { null },
                        defaultCycleMode = cycleMode!!,
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
