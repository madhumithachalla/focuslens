package com.focuslens

import android.content.Context
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.FilterChip
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

/** One suggested win. [why] says why it is suggested, so it is never a random tip. */
data class RewardIdea(val text: String, val category: String, val why: String)

val REWARD_CATEGORIES: List<String> = listOf("Movement", "Calm", "Treats", "People", "Creative", "Learning", "Rest", "Fun", "Reflect")

private val REWARD_IDEAS = listOf(
    RewardIdea("Go for a 15-minute walk without your phone.", "Movement",
        "Sitting for a long focus block drains attention. A short walk resets it."),
    RewardIdea("Stretch for ten minutes, slowly.", "Movement",
        "Stiff muscles after sitting make the next task feel heavier. Stretching loosens them."),
    RewardIdea("Dance to one song you love, all the way through.", "Movement",
        "A quick burst of movement lifts mood and breaks a slump."),

    RewardIdea("Make a cup of tea and sit by a window for five minutes.", "Calm",
        "Going from a screen straight to the next task keeps your mind on the screen. A pause lets it settle."),
    RewardIdea("Step outside and name five things you can see, hear and smell.", "Calm",
        "Naming things around you pulls attention out of worry or scrolling and into the present."),
    RewardIdea("Water a plant, or look at the sky for a minute.", "Calm",
        "Looking far away for a minute rests the eyes that have been close to a screen."),

    RewardIdea("Have your favourite meal, and eat it slowly.", "Treats",
        "You got time back. Enjoying it on purpose turns that time into something you chose."),
    RewardIdea("Cook one simple meal from scratch.", "Treats",
        "Making something with your hands is a clear, finished win you can see."),
    RewardIdea("Buy yourself a small flower or a treat you've wanted.", "Treats",
        "A small, planned reward after a good stretch makes the good stretch easier to repeat."),
    RewardIdea("Take yourself on a solo date: a café, a walk, a bookshop, no phone.", "Treats",
        "Spending time on yourself without a screen is a reward that doesn't depend on anyone else."),

    RewardIdea("Call someone you love and tell them one good thing about your week.", "People",
        "Sharing a win with someone turns it from private progress into something that is noticed."),
    RewardIdea("Message a friend a photo of something that made you smile today.", "People",
        "A quick connection is cheap to do and often lifts the day for both of you."),
    RewardIdea("Do one kind thing for someone without telling them.", "People",
        "Doing something kind for someone else gives a lift that lasts past the moment."),

    RewardIdea("Sketch, doodle or write a short poem for five minutes.", "Creative",
        "Making something with no goal gives your brain a break from measuring progress."),
    RewardIdea("Make a playlist for your next focus block.", "Creative",
        "Setting up your next session now makes starting it easier later."),

    RewardIdea("Read a chapter of a book you've been meaning to finish.", "Learning",
        "Reading something you chose, without a deadline, feels like progress you can enjoy."),
    RewardIdea("Learn one new word in a language you're studying.", "Learning",
        "A small daily step adds up, and it is easy to finish in a few minutes."),

    RewardIdea("Take a short nap of 20 minutes, no longer.", "Rest",
        "Twenty minutes is long enough to refresh and short enough not to leave you groggy."),
    RewardIdea("Sit in the sun for ten minutes and do nothing at all.", "Rest",
        "Doing nothing on purpose lets your attention recover, which focused work needs."),
    RewardIdea("Take a long shower or bath with your favourite music.", "Rest",
        "A calm, sensory break helps the body come down after a stressful stretch."),

    RewardIdea("Watch an episode of something you genuinely love, guilt-free.", "Fun",
        "Enjoying something without a reason to justify it counts as a reward, not a slip."),
    RewardIdea("Play a game or a puzzle just for fun for fifteen minutes.", "Fun",
        "A game with a clear end gives a win you can finish, then stop."),

    RewardIdea("Write down three things that went well today.", "Reflect",
        "Noticing what worked makes it easier to do again tomorrow."),
    RewardIdea("Tidy one drawer or one corner of your desk.", "Reflect",
        "A small, visible tidy-up is a finished job you can see straight away."),
)

private const val REWARDS_PREFS = "rewards_prefs"
private const val KEY_DONE = "done_count"

/** How many wins you've marked as done, in total. Stored on this phone only. */
fun rewardsDone(context: Context): Int =
    context.getSharedPreferences(REWARDS_PREFS, Context.MODE_PRIVATE).getInt(KEY_DONE, 0)

/** Wins per category, for the summary under the card. */
fun rewardsDoneByCategory(context: Context): Map<String, Int> {
    val sp = context.getSharedPreferences(REWARDS_PREFS, Context.MODE_PRIVATE)
    return REWARD_CATEGORIES.associateWith { sp.getInt("cat_$it", 0) }
}

private fun markRewardDone(context: Context, category: String) {
    val sp = context.getSharedPreferences(REWARDS_PREFS, Context.MODE_PRIVATE)
    sp.edit()
        .putInt(KEY_DONE, sp.getInt(KEY_DONE, 0) + 1)
        .putInt("cat_$category", sp.getInt("cat_$category", 0) + 1)
        .apply()
}

@Composable
fun RewardsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    // "All" (null) or one category.
    var filter by remember { mutableStateOf<String?>(null) }
    var pick by remember { mutableIntStateOf(-1) }
    var done by remember { mutableIntStateOf(rewardsDone(context)) }
    var byCategory by remember { mutableStateOf(rewardsDoneByCategory(context)) }
    var lastDone by remember { mutableStateOf(false) }

    val pool = REWARD_IDEAS.filter { filter == null || it.category == filter }
    fun randomIndex(exclude: Int): Int {
        if (pool.size <= 1) return 0
        var i = pool.indices.random()
        while (i == exclude) i = pool.indices.random()
        return i
    }
    // Read the index safely while drawing; never write state during composition.
    val shown = if (pick in pool.indices) pick else 0
    val idea = pool.getOrNull(shown)

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Rewards", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Wins for the time you just got back. Pick a kind of win, or leave it on All.",
            fontSize = 14.sp, color = Color.Gray
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == null, onClick = { filter = null; pick = -1 }, label = { Text("All") })
            REWARD_CATEGORIES.forEach { c ->
                FilterChip(selected = filter == c, onClick = { filter = c; pick = -1 }, label = { Text(c) })
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text((idea?.category ?: "").uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(Modifier.height(6.dp))
                Text(idea?.text ?: "", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text("Why this: ${idea?.why ?: ""}", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(16.dp))
                Row {
                    Button(enabled = idea != null, onClick = {
                        val cat = idea?.category ?: return@Button
                        markRewardDone(context, cat)
                        done = rewardsDone(context)
                        byCategory = rewardsDoneByCategory(context)
                        lastDone = true
                    }) { Text("I did it") }
                    Spacer(Modifier.padding(horizontal = 6.dp))
                    OutlinedButton(onClick = {
                        pick = randomIndex(shown)
                        lastDone = false
                    }) { Text("Suggest another") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (lastDone) "Nice. That's $done win${if (done == 1) "" else "s"} so far, and it counts toward your achievements."
            else "You've logged $done win${if (done == 1) "" else "s"} so far.",
            fontSize = 13.sp, color = Color.Gray
        )
        Spacer(Modifier.height(12.dp))
        Text("Wins by kind", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        byCategory.filterValues { it > 0 }.forEach { (c, n) ->
            Text("$c: $n", fontSize = 13.sp, color = Color.Gray)
        }
        if (byCategory.values.all { it == 0 }) Text("None yet.", fontSize = 13.sp, color = Color.Gray)
    }
}
