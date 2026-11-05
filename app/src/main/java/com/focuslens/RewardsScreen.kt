package com.focuslens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small real-world things worth doing with time you just got back. Plain list, no points. */
private val REWARD_ACTIVITIES = listOf(
    "Go for a 15-minute walk without your phone." to "Movement",
    "Make a cup of tea and sit by a window for five minutes." to "Calm",
    "Cook one simple meal from scratch." to "Home",
    "Call or message someone you haven't spoken to in a while." to "People",
    "Read a chapter of a book you've been meaning to finish." to "Learning",
    "Stretch for ten minutes, slowly." to "Body",
    "Sketch, doodle or write a short poem for five minutes." to "Creative",
    "Tidy one drawer or one corner of your desk." to "Home",
    "Take a short nap of 20 minutes, no longer." to "Rest",
    "Step outside and name five things you can see, hear and smell." to "Calm",
    "Learn one new word in a language you're studying." to "Learning",
    "Water a plant, or go and look at the sky for a minute." to "Calm",
    "Write down three things that went well today." to "Reflect",
    "Dance to one song you love, all the way through." to "Movement",
    "Do one kind thing for someone without telling them." to "People",
    "Call someone you love and tell them one good thing about your week." to "People",
    "Have your favourite meal, and eat it slowly." to "Treat",
    "Take yourself on a solo date: a café, a walk, a bookshop, no phone." to "Treat",
    "Watch an episode of something you genuinely love, guilt-free." to "Fun",
    "Buy yourself a small flower or a treat you've wanted." to "Treat",
    "Play a game or a puzzle just for fun for fifteen minutes." to "Fun",
    "Take a long shower or bath with your favourite music." to "Rest",
    "Message a friend a photo of something that made you smile today." to "People",
    "Sit in the sun for ten minutes and do nothing at all." to "Rest",
    "Make a playlist for your next focus block." to "Creative",
)

private const val REWARDS_PREFS = "rewards_prefs"
private const val KEY_DONE = "done_count"

/** How many rewards you've marked as done. Stored on this phone only. */
fun rewardsDone(context: Context): Int =
    context.getSharedPreferences(REWARDS_PREFS, Context.MODE_PRIVATE).getInt(KEY_DONE, 0)

private fun markRewardDone(context: Context) {
    val sp = context.getSharedPreferences(REWARDS_PREFS, Context.MODE_PRIVATE)
    sp.edit().putInt(KEY_DONE, sp.getInt(KEY_DONE, 0) + 1).apply()
}

@Composable
fun RewardsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var index by remember { mutableIntStateOf((REWARD_ACTIVITIES.indices).random()) }
    var done by remember { mutableIntStateOf(rewardsDone(context)) }
    var lastDone by remember { mutableStateOf(false) }
    val (text, category) = REWARD_ACTIVITIES[index]

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Rewards", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Got a moment after a focus block or a blocked app? Try one of these.",
            fontSize = 14.sp, color = Color.Gray
        )
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(category.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(Modifier.height(6.dp))
                Text(text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(16.dp))
                Row {
                    Button(onClick = {
                        markRewardDone(context)
                        done = rewardsDone(context)
                        lastDone = true
                    }) { Text("I did it") }
                    Spacer(Modifier.padding(horizontal = 6.dp))
                    OutlinedButton(onClick = {
                        var next = (REWARD_ACTIVITIES.indices).random()
                        if (REWARD_ACTIVITIES.size > 1) while (next == index) next = (REWARD_ACTIVITIES.indices).random()
                        index = next
                        lastDone = false
                    }) { Text("Suggest another") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (lastDone) "Nice. That's ${done} reward${if (done == 1) "" else "s"} you've done, which counts toward your achievements."
            else "You've done $done reward${if (done == 1) "" else "s"} so far.",
            fontSize = 13.sp, color = Color.Gray
        )
    }
}
