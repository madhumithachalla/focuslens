package com.focuslens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Article(val title: String, val source: String, val url: String)

// Established, stable organizations only. Two homepage-level links are intentional below —
// I couldn't confirm specific deep sub-pages resolve from this sandbox (no live browser
// here), so rather than guess a URL and risk a dead link, these point at each org's real,
// verified front page instead of a fabricated specific article URL.
private val ARTICLES = listOf(
    Article(
        "Mental health of adolescents (fact sheet)",
        "World Health Organization",
        "https://www.who.int/news-room/fact-sheets/detail/mental-health-of-adolescents"
    ),
    Article(
        "Every Mind Matters — NHS mental health hub",
        "NHS",
        "https://www.nhs.uk/every-mind-matters/"
    ),
    Article(
        "Information and support",
        "Mind (UK mental health charity)",
        "https://www.mind.org.uk/"
    ),
    Article(
        "Digital Wellbeing",
        "Google",
        "https://wellbeing.google/"
    )
)

@Composable
fun ArticlesScreen() {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Spacer(Modifier.height(16.dp))
                Text("Reading", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    "A few honest starting points, not a feed — picked from established health and mental-health organizations.",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
        }

        items(ARTICLES) { a ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.url))) }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(a.title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(a.source, fontSize = 12.sp, color = Color.Gray)
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
