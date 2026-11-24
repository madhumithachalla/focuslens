package com.focuslens

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLDecoder

/** One multiple-choice question. [answer] is the index of the right option. */
data class InterceptQuestion(val category: String, val text: String, val options: List<String>, val answer: Int)

/**
 * Questions for the interception, grouped by category. Each try asks three questions from
 * three different categories, so the mix changes every time.
 */
object InterceptBank {
    private fun q(category: String, text: String, answer: Int, vararg options: String) =
        InterceptQuestion(category, text, options.toList(), answer)

    private val all: List<InterceptQuestion> = listOf(
        q("Books", "Who wrote Pride and Prejudice?", 0, "Jane Austen", "Charlotte Brontë", "Mary Shelley", "George Eliot"),
        q("Books", "Who wrote 1984?", 1, "Aldous Huxley", "George Orwell", "Ray Bradbury", "H. G. Wells"),
        q("Books", "Which novel begins with \"Call me Ishmael\"?", 0, "Moby-Dick", "Dracula", "Frankenstein", "Emma"),
        q("Books", "Who created Sherlock Holmes?", 1, "Agatha Christie", "Arthur Conan Doyle", "Edgar Allan Poe", "Bram Stoker"),
        q("Books", "Who wrote The Hobbit?", 1, "J. K. Rowling", "J. R. R. Tolkien", "C. S. Lewis", "Philip Pullman"),
        q("Books", "Who wrote To Kill a Mockingbird?", 0, "Harper Lee", "Toni Morrison", "John Steinbeck", "Zora Neale Hurston"),

        q("Movies", "Which film has a shark called Bruce?", 0, "Jaws", "Alien", "Titanic", "Gremlins"),
        q("Movies", "Who directed Jurassic Park (1993)?", 1, "James Cameron", "Steven Spielberg", "Ridley Scott", "George Lucas"),
        q("Movies", "Which film is set on a sinking ship?", 0, "Titanic", "Jaws", "Avatar", "Up"),
        q("Movies", "Which saga is about the Jedi and the Force?", 1, "Star Trek", "Star Wars", "Dune", "Interstellar"),
        q("Movies", "Which animated film features a clownfish called Nemo?", 0, "Finding Nemo", "Shark Tale", "Moana", "Up"),
        q("Movies", "Which film has the tagline \"In space, no one can hear you scream\"?", 0, "Alien", "Gravity", "Interstellar", "Sunshine"),

        q("Vocabulary", "What does \"ubiquitous\" mean?", 1, "Rare", "Found everywhere", "Very old", "Angry"),
        q("Vocabulary", "Which word means the same as \"brief\"?", 1, "Long", "Short", "Heavy", "Loud"),
        q("Vocabulary", "What does \"meticulous\" mean?", 1, "Careless", "Very careful", "Quick", "Lazy"),
        q("Vocabulary", "Which word is the opposite of \"benevolent\"?", 2, "Kind", "Generous", "Malevolent", "Gentle"),
        q("Vocabulary", "What does \"eloquent\" mean?", 0, "Well spoken", "Silent", "Confused", "Hungry"),
        q("Vocabulary", "What does \"resilient\" mean?", 1, "Fragile", "Able to recover quickly", "Lazy", "Transparent"),

        q("General knowledge", "What is the capital of Australia?", 2, "Sydney", "Melbourne", "Canberra", "Perth"),
        q("General knowledge", "Which is the largest ocean?", 3, "Atlantic", "Indian", "Arctic", "Pacific"),
        q("General knowledge", "How many continents are there?", 2, "5", "6", "7", "8"),
        q("General knowledge", "Which planet is known as the Red Planet?", 1, "Venus", "Mars", "Jupiter", "Mercury"),
        q("General knowledge", "What is the chemical symbol for gold?", 2, "Gd", "Ag", "Au", "Go"),
        q("General knowledge", "Which is the longest river in Africa?", 0, "Nile", "Congo", "Niger", "Zambezi"),

        q("English", "Which sentence is correct?", 0, "They're going home.", "Their going home.", "There going home.", "Theyre going home."),
        q("English", "What is the plural of \"child\"?", 1, "Childs", "Children", "Childes", "Childrens"),
        q("English", "Which word is a noun?", 1, "Quickly", "Happiness", "Run", "Blue"),
        q("English", "Which spelling is correct?", 1, "Accomodate", "Accommodate", "Acommodate", "Acomodate"),
        q("English", "What is the past tense of \"go\"?", 2, "Goed", "Gone", "Went", "Goes"),
        q("English", "The rain will ___ our plans.", 0, "affect", "effect", "affection", "effective"),

        q("Maths", "What is 15% of 240?", 1, "30", "36", "40", "48"),
        q("Maths", "What is 12 × 13?", 1, "144", "156", "162", "148"),
        q("Maths", "What is the square root of 144?", 2, "10", "11", "12", "14"),
        q("Maths", "What is 2 to the power of 10?", 2, "512", "1000", "1024", "2048"),
        q("Maths", "What is 3/4 as a decimal?", 2, "0.34", "0.5", "0.75", "0.8"),
        q("Maths", "A book costs $12. What do 4 books cost?", 2, "$44", "$46", "$48", "$52")
    )

    private const val PREFS = "focus"
    private const val CACHE = "intercept_live"
    private const val CACHED_AT = "intercept_live_at"
    private const val SIX_HOURS = 6L * 60 * 60 * 1000
    private const val SOURCE = "https://opentdb.com/api.php?amount=20&type=multiple&encode=url3986"

    /** Three questions from three different categories, in a random order. Live questions are mixed in. */
    fun pickThree(context: Context? = null): List<InterceptQuestion> {
        val pool = all + (context?.let { cached(it) } ?: emptyList())
        // Skip questions shown in the last few tries, unless a category has nothing else left.
        val recent = context?.let { recentTexts(it) } ?: emptySet()
        val groups = pool.groupBy { it.category }.values
            .map { g -> g.filter { it.text !in recent }.ifEmpty { g }.shuffled() }
            .shuffled()
        val picked = groups.take(3).map { it.first() }
        context?.let { rememberShown(it, picked) }
        return picked
    }

    private const val RECENT = "intercept_recent"
    private const val RECENT_LIMIT = 24

    private fun recentTexts(context: Context): Set<String> = try {
        val arr = JSONArray(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(RECENT, "[]"))
        (0 until arr.length()).map { arr.getString(it) }.toSet()
    } catch (e: Exception) {
        emptySet()
    }

    private fun rememberShown(context: Context, shown: List<InterceptQuestion>) {
        try {
            val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val arr = JSONArray(sp.getString(RECENT, "[]"))
            for (q in shown) arr.put(q.text)
            val kept = (0 until arr.length()).map { arr.getString(it) }.takeLast(RECENT_LIMIT)
            sp.edit().putString(RECENT, JSONArray(kept).toString()).apply()
        } catch (e: Exception) {
            // Not saving the history only means a question can repeat sooner.
        }
    }

    /** Fetches fresh questions in the background, at most every six hours. Failures keep the cache. */
    fun refreshIfStale(context: Context) {
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - sp.getLong(CACHED_AT, 0L) < SIX_HOURS) return
        Thread {
            try {
                val body = URL(SOURCE).openConnection().apply { connectTimeout = 8000; readTimeout = 8000 }
                    .getInputStream().bufferedReader().use { it.readText() }
                val results = JSONObject(body).getJSONArray("results")
                val out = JSONArray()
                for (i in 0 until results.length()) {
                    val r = results.getJSONObject(i)
                    val category = mapCategory(decode(r.getString("category"))) ?: continue
                    val wrong = r.getJSONArray("incorrect_answers")
                    val options = ArrayList<String>()
                    options.add(decode(r.getString("correct_answer")))
                    for (j in 0 until wrong.length()) options.add(decode(wrong.getString(j)))
                    val shuffled = options.shuffled()
                    val answer = shuffled.indexOf(decode(r.getString("correct_answer")))
                    out.put(JSONObject().put("c", category).put("q", decode(r.getString("question")))
                        .put("o", JSONArray(shuffled)).put("a", answer))
                }
                if (out.length() > 0) {
                    sp.edit().putString(CACHE, out.toString()).putLong(CACHED_AT, System.currentTimeMillis()).apply()
                }
            } catch (e: Exception) {
                // No internet or a bad response: the built-in questions still work.
            }
        }.start()
    }

    private fun cached(context: Context): List<InterceptQuestion> = try {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CACHE, null)
        val arr = JSONArray(raw ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val options = o.getJSONArray("o")
            InterceptQuestion(o.getString("c"), o.getString("q"), (0 until options.length()).map { options.getString(it) }, o.getInt("a"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun decode(text: String): String = try { URLDecoder.decode(text.replace("+", "%2B"), "UTF-8") } catch (e: Exception) { text }

    private fun mapCategory(source: String): String? = when {
        source.startsWith("Entertainment: Books") -> "Books"
        source.startsWith("Entertainment: Film") -> "Movies"
        source == "General Knowledge" -> "General knowledge"
        source == "Science: Mathematics" -> "Maths"
        source.startsWith("Entertainment: Music") -> "Music"
        source.startsWith("Entertainment: Television") -> "TV"
        source.startsWith("Entertainment: Video Games") -> "Video games"
        source == "Science: Computers" -> "Computers"
        source == "Science & Nature" -> "Science"
        source == "Geography" -> "Geography"
        source == "History" -> "History"
        source == "Animals" -> "Animals"
        source == "Sports" -> "Sports"
        else -> null
    }
}
