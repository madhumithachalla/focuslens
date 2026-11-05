package com.focuslens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class MediaKind(val label: String) { BOOK("Books"), MOVIE("Movies"), SERIES("Series") }

enum class MediaGenre(val label: String) {
    ALL("All"), PRODUCTIVITY("Productivity"), SCI_FI("Sci-fi"), COMEDY("Comedy"),
    DRAMA("Drama"), DOCUMENTARY("Documentary"), MEMOIR("Memoir & Non-fiction"), FEEL_GOOD("Feel-good")
}

data class MediaItem(
    val title: String,
    val creator: String,
    val year: Int,
    val kind: MediaKind,
    val genres: Set<MediaGenre>,
    val blurb: String
)

// Real, correctly-attributed titles only — this is a short curated starter list, not a
// live catalog, so it won't reflect every new release. No links out: where to watch or
// buy changes by region and service too often to point at honestly from here.
private val MEDIA_ITEMS = listOf(
    MediaItem("Atomic Habits", "James Clear", 2018, MediaKind.BOOK, setOf(MediaGenre.PRODUCTIVITY),
        "Small, consistent changes compound — the book behind most modern habit-building advice."),
    MediaItem("Deep Work", "Cal Newport", 2016, MediaKind.BOOK, setOf(MediaGenre.PRODUCTIVITY),
        "The case for long, undistracted stretches of focused work in a world built to fragment it."),
    MediaItem("Sapiens", "Yuval Noah Harari", 2011, MediaKind.BOOK, setOf(MediaGenre.MEMOIR),
        "A sweeping, readable history of how humans ended up running the planet."),
    MediaItem("Educated", "Tara Westover", 2018, MediaKind.BOOK, setOf(MediaGenre.MEMOIR, MediaGenre.DRAMA),
        "A memoir about growing up off-grid and the cost and value of an education found later."),
    MediaItem("Project Hail Mary", "Andy Weir", 2021, MediaKind.BOOK, setOf(MediaGenre.SCI_FI),
        "A lone astronaut, a dying sun, and a puzzle-box plot from the author of The Martian."),
    MediaItem("The Midnight Library", "Matt Haig", 2020, MediaKind.BOOK, setOf(MediaGenre.FEEL_GOOD, MediaGenre.DRAMA),
        "A quiet, hopeful novel about regret and the lives you didn't live."),

    MediaItem("Inside Out", "Pixar, dir. Pete Docter", 2015, MediaKind.MOVIE, setOf(MediaGenre.FEEL_GOOD),
        "Emotions as characters inside a kid's head — funnier and sadder than it sounds."),
    MediaItem("Soul", "Pixar, dir. Pete Docter", 2020, MediaKind.MOVIE, setOf(MediaGenre.FEEL_GOOD, MediaGenre.DRAMA),
        "What actually makes a life worth living, wrapped in a jazz musician's afterlife detour."),
    MediaItem("The Secret Life of Walter Mitty", "dir. Ben Stiller", 2013, MediaKind.MOVIE, setOf(MediaGenre.FEEL_GOOD),
        "A daydreamer finally goes and does the things he's only imagined."),
    MediaItem("Spotlight", "dir. Tom McCarthy", 2015, MediaKind.MOVIE, setOf(MediaGenre.DRAMA),
        "Journalists doing patient, careful, unglamorous work that actually matters. Best Picture, 2016."),
    MediaItem("Everything Everywhere All at Once", "dir. the Daniels", 2022, MediaKind.MOVIE, setOf(MediaGenre.SCI_FI, MediaGenre.COMEDY),
        "A multiverse kung-fu tax audit that somehow lands as one of the most moving films in years."),

    MediaItem("Ted Lasso", "Apple TV+", 2020, MediaKind.SERIES, setOf(MediaGenre.COMEDY, MediaGenre.FEEL_GOOD),
        "An American football coach leads an English football team, armed only with relentless kindness."),
    MediaItem("The Bear", "FX / Hulu", 2022, MediaKind.SERIES, setOf(MediaGenre.DRAMA, MediaGenre.COMEDY),
        "A fine-dining chef returns to run his late brother's sandwich shop. Loud, tender, stressful in a good way."),
    MediaItem("Abbott Elementary", "ABC", 2021, MediaKind.SERIES, setOf(MediaGenre.COMEDY),
        "A mockumentary about underfunded teachers who keep showing up anyway."),
    MediaItem("Brooklyn Nine-Nine", "NBC / Fox", 2013, MediaKind.SERIES, setOf(MediaGenre.COMEDY),
        "A precinct of very silly detectives. Easy to watch an episode and stop — or not."),
    MediaItem("Planet Earth II", "BBC", 2016, MediaKind.SERIES, setOf(MediaGenre.DOCUMENTARY),
        "Nature documentary filmmaking at its most jaw-dropping, narrated by David Attenborough.")
)

@Composable
fun MediaSuggestionsScreen() {
    var genre by remember { mutableStateOf(MediaGenre.ALL) }
    val visible = remember(genre) {
        if (genre == MediaGenre.ALL) MEDIA_ITEMS else MEDIA_ITEMS.filter { genre in it.genres }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Spacer(Modifier.height(16.dp))
                Text("Explore", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Books, movies and series worth your reclaimed time — a short curated starter list, " +
                        "not a live catalog. Pick an interest to narrow it down.",
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
                MediaGenre.values().forEach { g ->
                    FilterChip(selected = genre == g, onClick = { genre = g }, label = { Text(g.label) })
                }
            }
        }

        MediaKind.values().forEach { kind ->
            val kindItems = visible.filter { it.kind == kind }
            if (kindItems.isNotEmpty()) {
                item {
                    Text(
                        kind.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1F3A34)
                    )
                }
                items(kindItems, key = { it.title }) { m ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${m.title} (${m.year})", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(m.creator, fontSize = 12.sp, color = Color.Gray)
                            Spacer(Modifier.height(6.dp))
                            Text(m.blurb, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
