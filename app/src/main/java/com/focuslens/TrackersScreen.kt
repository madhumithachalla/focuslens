package com.focuslens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WATER_PRESETS = listOf(300, 500, 1000, 2000, 3000)
private val BAR_WATER = Color(0xFF4EA8F7)
private val BAR_PAGES = Color(0xFF3A5245)

private fun mlLabel(ml: Int) = if (ml >= 1000 && ml % 1000 == 0) "${ml / 1000} L" else "$ml ml"

/** Bars for each of the last days, with a label under each. */
@Composable
private fun DayBars(values: List<Int>, labels: List<String>, color: Color, unit: String) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(90.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEach { v ->
            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Box(Modifier.fillMaxWidth().height((80.dp * (v.toFloat() / max)).coerceAtLeast(2.dp)).background(if (v > 0) color else Color(0xFFE3E0D8)))
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { i, l ->
            Text("$l\n${values[i]}$unit", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.weight(1f))
        }
    }
}

/** Water and reading in one page. Both save on this phone only. */
@Composable
fun TrackersScreen() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var customMl by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    var bookTitle by remember { mutableStateOf("") }
    var bookPages by remember { mutableStateOf("") }
    var pagesFor by remember { mutableStateOf<Long?>(null) }
    var pagesInput by remember { mutableStateOf("") }

    val dayLabels = listOf("Today", "Yest.") + (2..6).map { back ->
        java.text.SimpleDateFormat("EEE", java.util.Locale.US).format(TrackersStore.dayStart(back))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Water and reading", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        message?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }
        Spacer(Modifier.height(12.dp))

        // ---------------- Water ----------------
        val drunk = remember(tick) { TrackersStore.waterByDay(context, 1)[0] }
        val goal = remember(tick) { TrackersStore.waterGoalMl(context) }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Water today", fontWeight = FontWeight.Medium)
                Text("${mlLabel(drunk)} of ${mlLabel(goal)}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (drunk.toFloat() / goal.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(), color = BAR_WATER, trackColor = Color(0xFFE3E0D8)
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WATER_PRESETS.forEach { ml ->
                        FilterChip(selected = false, onClick = {
                            TrackersStore.addDrink(context, ml); tick++; message = "Added ${mlLabel(ml)}."
                        }, label = { Text("+ ${mlLabel(ml)}") })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customMl, onValueChange = { customMl = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text("Other amount (ml)") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                    Button(enabled = (customMl.toIntOrNull() ?: 0) in 1..5000, onClick = {
                        TrackersStore.addDrink(context, customMl.toInt()); tick++
                        message = "Added ${customMl} ml."; customMl = ""
                    }) { Text("Add") }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { TrackersStore.undoLastDrink(context); tick++; message = "Last drink removed." }) { Text("Undo last") }
                    Text("Goal:", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 12.dp))
                    listOf(1500, 2000, 2500, 3000).forEach { g ->
                        FilterChip(selected = goal == g, onClick = { TrackersStore.setWaterGoalMl(context, g); tick++ }, label = { Text(mlLabel(g)) })
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Last 7 days", fontWeight = FontWeight.Medium)
        DayBars(remember(tick) { TrackersStore.waterByDay(context, 7) }, dayLabels, BAR_WATER, " ml")

        // ---------------- Reading ----------------
        Spacer(Modifier.height(20.dp))
        val books = remember(tick) { TrackersStore.books(context) }
        val pagesToday = remember(tick) { TrackersStore.pagesByDay(context, 1)[0] }
        val pagesWeek = remember(tick) { TrackersStore.pagesByDay(context, 7).sum() }
        val pagesAll = books.sumOf { it.pagesRead }
        Text("Reading", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Pages today: $pagesToday · this week: $pagesWeek · all books: $pagesAll", fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        Text("Pages per day, last 7 days", fontWeight = FontWeight.Medium)
        DayBars(remember(tick) { TrackersStore.pagesByDay(context, 7) }, dayLabels, BAR_PAGES, " p")
        Spacer(Modifier.height(10.dp))

        Text("Add a book", fontWeight = FontWeight.Medium)
        OutlinedTextField(value = bookTitle, onValueChange = { bookTitle = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = bookPages, onValueChange = { bookPages = it.filter { c -> c.isDigit() }.take(5) },
            label = { Text("Total pages (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Button(enabled = bookTitle.isNotBlank(), onClick = {
            val list = TrackersStore.books(context) + TrackersStore.Book(
                System.currentTimeMillis(), bookTitle.trim(), bookPages.toIntOrNull() ?: 0, emptyList()
            )
            TrackersStore.saveBooks(context, list)
            bookTitle = ""; bookPages = ""; tick++
            message = "Book added."
        }) { Text("Add book") }

        Spacer(Modifier.height(12.dp))
        if (books.isEmpty()) Text("No books yet.", fontSize = 13.sp, color = Color.Gray)
        books.forEach { b ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(b.title, fontWeight = FontWeight.Medium)
                    Text(
                        if (b.totalPages > 0) "${b.pagesRead} of ${b.totalPages} pages"
                        else "${b.pagesRead} pages read",
                        fontSize = 12.sp, color = Color.Gray
                    )
                    if (b.totalPages > 0) {
                        LinearProgressIndicator(
                            progress = { (b.pagesRead.toFloat() / b.totalPages).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(), color = BAR_PAGES, trackColor = Color(0xFFE3E0D8)
                        )
                    }
                    if (pagesFor == b.id) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = pagesInput, onValueChange = { pagesInput = it.filter { c -> c.isDigit() }.take(4) },
                                label = { Text("Pages read") }, singleLine = true, modifier = Modifier.weight(1f)
                            )
                            Button(enabled = (pagesInput.toIntOrNull() ?: 0) > 0, onClick = {
                                val n = pagesInput.toInt()
                                val updated = TrackersStore.books(context).map { x ->
                                    if (x.id == b.id) x.copy(sessions = x.sessions + TrackersStore.Session(System.currentTimeMillis(), n)) else x
                                }
                                TrackersStore.saveBooks(context, updated)
                                pagesInput = ""; pagesFor = null; tick++
                                message = "Logged $n pages for ${b.title}."
                            }) { Text("Log") }
                        }
                    } else {
                        OutlinedButton(onClick = { pagesFor = b.id }) { Text("Log pages") }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
