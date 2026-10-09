package com.focuslens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A small, real-world thing to do with time just reclaimed from a blocked app or a
 * finished focus session — not a game mechanic, just a nudge toward something considerate
 * or restorative. Plain curated list, no AI generation and no notion of "points" or
 * "levels" (those would turn a break into another thing to optimize).
 */
private data class RewardIdea(val category: String, val text: String)

private val REWARD_IDEAS = listOf(
    RewardIdea("Move", "Step outside for 5 minutes, even just to the doorway."),
    RewardIdea("Move", "Stretch your neck and shoulders for 30 seconds."),
    RewardIdea("Move", "Do 10 slow squats or a short walk around the room."),
    RewardIdea("Rest your eyes", "Look at something 20 feet away for 20 seconds."),
    RewardIdea("Rest your eyes", "Close your eyes and breathe slowly for 1 minute."),
    RewardIdea("Hydrate", "Drink a full glass of water."),
    RewardIdea("Connect", "Send a kind text to someone you haven't talked to in a while."),
    RewardIdea("Connect", "Call a friend or family member, even just for 2 minutes."),
    RewardIdea("Connect", "Say one genuine compliment to someone nearby."),
    RewardIdea("Create", "Doodle for 2 minutes, no pressure for it to look good."),
    RewardIdea("Create", "Hum or sing a song you like."),
    RewardIdea("Create", "Write down one idea you've been putting off."),
    RewardIdea("Tidy", "Tidy one surface near you."),
    RewardIdea("Tidy", "Reply to one message or email you've been avoiding."),
    RewardIdea("Reflect", "Write down one thing you're grateful for today."),
    RewardIdea("Reflect", "Write down what you want to do with the time you just got back.")
)

@Composable
fun RewardSuggestionCard(title: String = "Since you've got a moment") {
    var idea by remember { mutableStateOf(REWARD_IDEAS.random()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3EFE8))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 13.sp, color = Color(0xFF1F3A34), fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(idea.category.uppercase(), fontSize = 10.sp, color = Color(0xFF5B8372), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(idea.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    idea = REWARD_IDEAS.filter { it != idea }.randomOrNull() ?: idea
                }) {
                    Text("Something else")
                }
            }
        }
    }
}
