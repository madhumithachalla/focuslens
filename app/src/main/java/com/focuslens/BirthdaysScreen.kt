package com.focuslens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import com.focuslens.data.AppDatabase
import com.focuslens.data.BirthdayWishEntity
import com.focuslens.data.PersonEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun daysUntilBirthday(mmdd: String): Int {
    val month = mmdd.substring(0, 2).toIntOrNull() ?: return Int.MAX_VALUE
    val day = mmdd.substring(2, 4).toIntOrNull() ?: return Int.MAX_VALUE
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val next = (today.clone() as Calendar).apply {
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, day)
    }
    if (next.before(today)) next.add(Calendar.YEAR, 1)
    return ((next.timeInMillis - today.timeInMillis) / 86_400_000L).toInt()
}

private fun formatMmdd(mmdd: String): String {
    val month = mmdd.substring(0, 2).toInt()
    val day = mmdd.substring(2, 4).toInt()
    val names = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return "${names[month - 1]} $day"
}

/**
 * Reads birthdays from the device's own Contacts app via the standard ContactsContract
 * provider — this already includes any Google account's contacts synced to the device, so it
 * covers the common "my contacts/Gmail have birthdays in them" case without needing a Google
 * Cloud OAuth app, API enablement, or Google's app-verification review. Only runs after the
 * user explicitly taps "Import from Contacts" and grants the permission prompt.
 */
private fun importBirthdaysFromContacts(context: Context): List<Pair<String, String>> {
    val results = mutableListOf<Pair<String, String>>()
    val resolver = context.contentResolver
    val cursor = resolver.query(
        ContactsContract.CommonDataKinds.Event.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Event.DISPLAY_NAME, ContactsContract.CommonDataKinds.Event.START_DATE),
        "${ContactsContract.CommonDataKinds.Event.TYPE} = ?",
        arrayOf(ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()),
        null
    )
    cursor?.use {
        val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Event.DISPLAY_NAME)
        val dateIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Event.START_DATE)
        while (it.moveToNext()) {
            val name = (if (nameIdx >= 0) it.getString(nameIdx) else null) ?: continue
            val rawDate = (if (dateIdx >= 0) it.getString(dateIdx) else null) ?: continue
            // Android stores this as yyyy-MM-dd, or --MM-dd when no year was given.
            val parts = rawDate.split("-")
            if (parts.size == 3) {
                val month = parts[1].toIntOrNull() ?: continue
                val day = parts[2].toIntOrNull() ?: continue
                results.add(name to "%02d%02d".format(month, day))
            }
        }
    }
    return results
}

@Composable
fun BirthdaysScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()

    var people by remember { mutableStateOf<List<PersonEntity>>(emptyList()) }
    var wishedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var newName by remember { mutableStateOf("") }
    var newBirthdayMillis by remember { mutableStateOf<Long?>(null) }
    var pickingNewBirthday by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }

    suspend fun refresh() {
        people = db.personDao().allOnce()
        wishedIds = db.birthdayWishDao().forYear(currentYear).filter { it.wished }.map { it.personId }.toSet()
        loading = false
    }

    suspend fun runImport() {
        val imported = importBirthdaysFromContacts(context)
        imported.forEach { (name, mmdd) ->
            db.personDao().insert(PersonEntity(name = name, birthday = mmdd, source = "device_contacts", createdAt = System.currentTimeMillis()))
        }
        refresh()
        importStatus = if (imported.isEmpty())
            "No contacts with a saved birthday were found."
        else
            "Imported ${imported.size} birthday${if (imported.size == 1) "" else "s"} from your contacts."
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) scope.launch { runImport() }
        else importStatus = "Permission not granted — add birthdays manually below instead."
    }

    LaunchedEffect(Unit) { refresh() }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val upcoming = people.filter { !it.birthday.isNullOrBlank() }.sortedBy { daysUntilBirthday(it.birthday!!) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("Birthdays", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Upcoming birthdays, and a checklist of who's wished you on yours.", fontSize = 13.sp, color = Color.Gray)
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Import from Contacts", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Reads names and birthdays already in your phone's Contacts app — this includes any " +
                            "Google account's contacts synced to this device, not just ones added manually. " +
                            "Nothing is uploaded anywhere; it's a one-time local copy into FocusLens, only if " +
                            "you tap below and grant the permission.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                        if (granted) scope.launch { runImport() }
                        else contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }) {
                        Text("Import from Contacts")
                    }
                    importStatus?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Add someone manually", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { pickingNewBirthday = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(newBirthdayMillis?.let { "Birthday: ${formatPickedDate(it)}" } ?: "Pick birthday (optional)")
                    }
                    if (pickingNewBirthday) {
                        DatePickerDialogPicker(
                            initial = newBirthdayMillis,
                            onPicked = { newBirthdayMillis = it; pickingNewBirthday = false },
                            onDismiss = { pickingNewBirthday = false }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            val mmdd = newBirthdayMillis?.let { SimpleDateFormat("MMdd", Locale.US).format(Date(it)) }
                            scope.launch {
                                db.personDao().insert(
                                    PersonEntity(name = newName.trim(), birthday = mmdd, source = "manual", createdAt = System.currentTimeMillis())
                                )
                                newName = ""
                                newBirthdayMillis = null
                                refresh()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add")
                    }
                }
            }
        }

        item { Text("Upcoming birthdays", fontSize = 16.sp, fontWeight = FontWeight.Bold) }

        if (upcoming.isEmpty()) {
            item { Text("None added yet.", fontSize = 13.sp, color = Color.Gray) }
        }
        items(upcoming) { person ->
            val days = daysUntilBirthday(person.birthday!!)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(person.name, fontSize = 14.sp)
                Text(
                    when (days) {
                        0 -> "Today!"
                        1 -> "Tomorrow"
                        else -> "In $days days (${formatMmdd(person.birthday!!)})"
                    },
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("$currentYear birthday wishes", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "Tick off who's wished you this year so you don't lose track of who to thank or wish back. " +
                    "Nothing here reads your messages or calls automatically — you check these off yourself.",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        if (people.isEmpty()) {
            item { Text("Add people above to start a checklist.", fontSize = 13.sp, color = Color.Gray) }
        }
        items(people) { person ->
            val checked = person.id in wishedIds
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { isChecked ->
                        scope.launch {
                            db.birthdayWishDao().upsert(BirthdayWishEntity(year = currentYear, personId = person.id, wished = isChecked))
                            refresh()
                        }
                    }
                )
                Text(person.name, fontSize = 14.sp)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
