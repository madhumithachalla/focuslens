package com.focuslens

import kotlin.random.Random

// Adaptive question engine for the "intercept": the question you answer before a blocked app
// opens. Kotlin port of docs/quiz-engine.js. Pure Kotlin (no Android types) so it can be unit
// tested. Math and logic questions are generated (answers are computed, never typed); the other
// categories are a small written bank. Levels run 1 (easy) to 5 (hard).

data class QuizCategory(val id: String, val label: String)

val QUIZ_CATEGORIES: List<QuizCategory> = listOf(
    QuizCategory("math", "Math"),
    QuizCategory("logic", "Logic patterns"),
    QuizCategory("language", "Words"),
    QuizCategory("science", "Science"),
    QuizCategory("general", "General knowledge"),
    QuizCategory("wellbeing", "Focus & wellbeing")
)

const val QUIZ_MAX_LEVEL = 5

/** The quiz is not user-configurable: 3 correct answers, 10 seconds before the first question. */
const val QUIZ_FIXED_NEEDED = 3
const val QUIZ_FIXED_DELAY_SECONDS = 10

data class QuizQuestion(
    val id: String,
    val category: String,
    val level: Int,
    val prompt: String,
    val choices: List<String>,
    val answerIndex: Int,
    val explain: String
)

/** A question the user wrote themselves. */
data class CustomQuestion(
    val prompt: String,
    val choices: List<String>,
    val answerIndex: Int,
    val explain: String
)

data class QuizAnswerResult(val right: Boolean, val explain: String, val correctIndex: Int, val done: Boolean)

private class BankItem(val prompt: String, val correct: String, val wrong: List<String>, val explain: String)

private fun intIn(rng: Random, lo: Int, hi: Int): Int = lo + (rng.nextDouble() * (hi - lo + 1)).toInt()

private fun <T> pickOne(rng: Random, items: List<T>): T = items[(rng.nextDouble() * items.size).toInt()]

private fun <T> shuffled(rng: Random, items: List<T>): List<T> {
    val a = items.toMutableList()
    for (i in a.size - 1 downTo 1) {
        val j = (rng.nextDouble() * (i + 1)).toInt()
        val t = a[i]
        a[i] = a[j]
        a[j] = t
    }
    return a
}

private class Choices(val choices: List<String>, val answerIndex: Int)

/** Four unique numeric choices: the answer plus plausible near misses. */
private fun numericChoices(rng: Random, answer: Int, spread: Int): Choices {
    val set = LinkedHashSet<Int>()
    set.add(answer)
    var guard = 0
    while (set.size < 4 && guard++ < 50) {
        val delta = intIn(rng, 1, spread) * (if (rng.nextDouble() < 0.5) -1 else 1)
        val v = answer + delta
        if (v != answer) set.add(v)
    }
    var pad = 1
    while (set.size < 4) set.add(answer + spread + pad++)
    val ordered = shuffled(rng, set.toList())
    return Choices(ordered.map { it.toString() }, ordered.indexOf(answer))
}

// ---------------------------------------------------------------- Generated: math

private fun mathQuestion(rng: Random, level: Int): QuizQuestion {
    val prompt: String
    val answer: Int
    val explain: String
    if (level == 1) {
        val a = intIn(rng, 3, 19)
        val b = intIn(rng, 2, 9)
        prompt = "$a + $b"; answer = a + b; explain = "$a plus $b is $answer."
    } else if (level == 2) {
        val a = intIn(rng, 24, 89)
        val b = intIn(rng, 11, 48)
        if (rng.nextDouble() < 0.5) {
            prompt = "$a + $b"; answer = a + b
        } else {
            prompt = "${a + b} − $b"; answer = a
        }
        explain = "Work it out column by column: $answer."
    } else if (level == 3) {
        val a = intIn(rng, 6, 15)
        val b = intIn(rng, 4, 14)
        prompt = "$a × $b"; answer = a * b; explain = "$a times $b is $answer."
    } else if (level == 4) {
        val a = intIn(rng, 3, 12)
        val b = intIn(rng, 3, 12)
        val c = intIn(rng, 2, 9)
        prompt = "$a + $b × $c"; answer = a + b * c
        explain = "Multiply first: $b × $c = ${b * c}, then add $a to get $answer."
    } else {
        val kind = intIn(rng, 0, 2)
        if (kind == 0) {
            val pct = pickOne(rng, listOf(5, 10, 15, 20, 25, 30, 40, 60, 75))
            val base = pickOne(rng, listOf(40, 80, 120, 160, 200, 240, 360, 480))
            prompt = "$pct% of $base"; answer = (pct * base) / 100
            explain = "$pct% of $base = $pct × $base ÷ 100 = $answer."
        } else if (kind == 1) {
            val x = intIn(rng, 3, 15)
            val m = intIn(rng, 2, 9)
            val k = intIn(rng, 4, 30)
            prompt = "If ${m}x + $k = ${m * x + k}, what is x"; answer = x
            explain = "Subtract $k, then divide by $m: x = $x."
        } else {
            val a = intIn(rng, 12, 25)
            val b = intIn(rng, 6, 14)
            prompt = "$a² − $b²"; answer = a * a - b * b
            explain = "${a * a} − ${b * b} = $answer."
        }
    }
    val spread = if (level <= 2) 6 else if (level == 3) 12 else 20
    val c = numericChoices(rng, answer, spread)
    val text = if (prompt.startsWith("If ")) "$prompt?" else "What is $prompt?"
    return QuizQuestion("", "math", level, text, c.choices, c.answerIndex, explain)
}

// ---------------------------------------------------------------- Generated: number patterns

private fun logicQuestion(rng: Random, level: Int): QuizQuestion {
    var seq: List<Int>
    val answer: Int
    val explain: String
    if (level == 1) {
        val start = intIn(rng, 1, 20)
        val step = intIn(rng, 2, 7)
        seq = (0..3).map { start + it * step }; answer = start + 4 * step
        explain = "The sequence goes up by $step each time."
    } else if (level == 2) {
        val a = intIn(rng, 2, 9)
        val b = intIn(rng, 11, 19)
        val s1 = intIn(rng, 2, 4)
        val s2 = intIn(rng, 3, 6)
        seq = listOf(a, b, a + s1, b + s2, a + 2 * s1); answer = b + 2 * s2
        explain = "Two interleaved lines: one adds $s1, the other adds $s2."
    } else if (level == 3) {
        val start = intIn(rng, 2, 5)
        val r = intIn(rng, 2, 3)
        var p = 1
        val terms = ArrayList<Int>()
        for (i in 0..4) {
            terms.add(start * p)
            p *= r
        }
        seq = terms.subList(0, 4).toList(); answer = terms[4]
        explain = "Each number is $r times the one before."
    } else if (level == 4) {
        val a = intIn(rng, 1, 4)
        val b = intIn(rng, 2, 6)
        val s = ArrayList<Int>()
        s.add(a)
        s.add(b)
        for (i in 2 until 6) s.add(s[i - 1] + s[i - 2])
        answer = s[5] + s[4]; seq = s.toList()
        explain = "Each number is the sum of the two before it."
    } else {
        val k = intIn(rng, 1, 4)
        seq = (1..5).map { it * it + k * it }; answer = 36 + 6 * k
        explain = "The n-th term is n² + ${k}n, so the 6th is $answer."
    }
    val c = numericChoices(rng, answer, if (level < 3) 6 else 18)
    return QuizQuestion(
        "", "logic", level, "What comes next? ${seq.joinToString(", ")}, …",
        c.choices, c.answerIndex, explain
    )
}

// ---------------------------------------------------------------- Written bank (copied verbatim from quiz-engine.js)
// Each entry: prompt, correct answer, three wrong answers, explanation. Only facts that are stable.

private val BANK: Map<String, Map<Int, List<BankItem>>> = mapOf(
        "language" to mapOf(
            1 to listOf(
                BankItem("Which word means the opposite of 'hot'?", "Cold", listOf("Warm", "Soft", "Fast"), "Cold is the opposite of hot."),
                BankItem("Which word is spelled correctly?", "Because", listOf("Becuase", "Becouse", "Beacause"), "Because: b-e-c-a-u-s-e."),
                BankItem("Which word means the same as 'big'?", "Large", listOf("Tiny", "Quiet", "Sharp"), "Large and big are synonyms."),
            ),
            2 to listOf(
                BankItem("Which word is spelled correctly?", "Definitely", listOf("Definately", "Definitly", "Defanitely"), "Definitely: de-fin-ite-ly."),
                BankItem("'Brief' is closest in meaning to:", "Short", listOf("Loud", "Careful", "Heavy"), "Brief means short in time or length."),
                BankItem("Complete the pair: hand is to glove as foot is to:", "Sock", listOf("Shoe lace", "Toe", "Leg"), "A glove covers a hand; a sock covers a foot."),
            ),
            3 to listOf(
                BankItem("'Meticulous' most nearly means:", "Very careful about detail", listOf("Easily angered", "Very tired", "Fond of travel"), "Meticulous describes extreme care with details."),
                BankItem("Choose the correct word: 'The new policy will ___ everyone in the office.'", "affect", listOf("effect", "affact", "efect"), "'Affect' is the verb; 'effect' is usually the noun."),
                BankItem("The opposite of 'scarce' is:", "Plentiful", listOf("Rare", "Costly", "Hidden"), "Scarce means in short supply; plentiful means the reverse."),
            ),
            4 to listOf(
                BankItem("'Ephemeral' most nearly means:", "Lasting a very short time", listOf("Extremely large", "Hard to believe", "Easy to see"), "Ephemeral things are short-lived."),
                BankItem("Which sentence uses 'its' correctly?", "The dog wagged its tail.", listOf("The dog wagged it's tail.", "Its a sunny day today.", "The book lost it's cover."), "'Its' is possessive; 'it's' means 'it is' or 'it has'."),
                BankItem("'Ubiquitous' most nearly means:", "Found everywhere", listOf("Hard to pronounce", "Brightly coloured", "Very old"), "Ubiquitous describes something present everywhere."),
            ),
            5 to listOf(
                BankItem("'Equivocal' most nearly means:", "Open to more than one interpretation", listOf("Perfectly balanced", "Clearly false", "Strongly opposed"), "An equivocal statement is ambiguous."),
                BankItem("Which word is a synonym for 'laconic'?", "Brief in speech", listOf("Talkative", "Sad", "Hasty"), "A laconic person uses few words."),
                BankItem("'Perfunctory' describes an action done:", "With minimal effort or interest", listOf("With great enthusiasm", "In secret", "At the wrong time"), "Perfunctory means routine and superficial."),
            ),
        ),
        "science" to mapOf(
            1 to listOf(
                BankItem("Which planet do we live on?", "Earth", listOf("Mars", "Venus", "Jupiter"), "We live on Earth."),
                BankItem("Water freezes into ice at what temperature (Celsius)?", "0°C", listOf("10°C", "50°C", "100°C"), "Pure water freezes at 0 °C at normal pressure."),
                BankItem("Which gas do humans need to breathe in to live?", "Oxygen", listOf("Helium", "Methane", "Argon"), "Our cells use oxygen for energy."),
            ),
            2 to listOf(
                BankItem("What is the closest star to Earth?", "The Sun", listOf("Proxima Centauri", "Sirius", "The North Star"), "The Sun is about 150 million km away."),
                BankItem("Plants take in which gas for photosynthesis?", "Carbon dioxide", listOf("Nitrogen", "Hydrogen", "Oxygen"), "Plants use carbon dioxide and light to make sugars."),
                BankItem("How many bones are in a typical adult human body?", "206", listOf("106", "306", "412"), "Adults have 206 bones."),
            ),
            3 to listOf(
                BankItem("What is the chemical symbol for sodium?", "Na", listOf("So", "Sd", "S"), "Na comes from the Latin 'natrium'."),
                BankItem("Which organ produces insulin?", "Pancreas", listOf("Liver", "Kidney", "Spleen"), "Insulin is made in the pancreas."),
                BankItem("Approximately how long does light from the Sun take to reach Earth?", "About 8 minutes", listOf("About 8 seconds", "About 8 hours", "About 1 day"), "Roughly 8 minutes 20 seconds."),
            ),
            4 to listOf(
                BankItem("What is the most abundant gas in Earth's atmosphere?", "Nitrogen", listOf("Oxygen", "Carbon dioxide", "Argon"), "About 78% is nitrogen."),
                BankItem("Which particle carries a negative electric charge?", "Electron", listOf("Proton", "Neutron", "Photon"), "Electrons are negatively charged."),
                BankItem("What does DNA stand for?", "Deoxyribonucleic acid", listOf("Dinitrogen acid", "Dioxyribose nucleic anion", "Deoxyribose nitrate acid"), "DNA is deoxyribonucleic acid."),
            ),
            5 to listOf(
                BankItem("Which neurotransmitter is most associated with reward and motivation?", "Dopamine", listOf("Insulin", "Cortisol", "Melatonin"), "Dopamine is central to reward and motivation circuits."),
                BankItem("The powerhouse of the cell, producing most of its ATP, is the:", "Mitochondrion", listOf("Ribosome", "Golgi apparatus", "Lysosome"), "Mitochondria make most of the cell's ATP."),
                BankItem("Which hormone is released mainly at night and signals sleep timing?", "Melatonin", listOf("Adrenaline", "Thyroxine", "Oxytocin"), "Melatonin rises in the evening in darkness."),
            ),
        ),
        "general" to mapOf(
            1 to listOf(
                BankItem("How many days are in a week?", "7", listOf("5", "6", "10"), "There are seven days in a week."),
                BankItem("Which is the largest ocean on Earth?", "Pacific", listOf("Atlantic", "Indian", "Arctic"), "The Pacific Ocean is the largest."),
                BankItem("How many minutes are in an hour?", "60", listOf("30", "100", "120"), "60 minutes make an hour."),
            ),
            2 to listOf(
                BankItem("What is the capital of Australia?", "Canberra", listOf("Sydney", "Melbourne", "Perth"), "Canberra is the capital."),
                BankItem("How many continents are there?", "7", listOf("5", "6", "8"), "By the common convention there are seven."),
                BankItem("Which language has the most native speakers?", "Mandarin Chinese", listOf("English", "Spanish", "Hindi"), "Mandarin has the most native speakers."),
            ),
            3 to listOf(
                BankItem("In which year did the first Moon landing happen?", "1969", listOf("1959", "1972", "1981"), "Apollo 11 landed in July 1969."),
                BankItem("Which element has the atomic number 1?", "Hydrogen", listOf("Helium", "Oxygen", "Carbon"), "Hydrogen is element number 1."),
                BankItem("What is the longest river in Africa?", "The Nile", listOf("The Congo", "The Niger", "The Zambezi"), "The Nile is generally cited as Africa's longest."),
            ),
            4 to listOf(
                BankItem("Who wrote the play 'Hamlet'?", "William Shakespeare", listOf("Christopher Marlowe", "Charles Dickens", "Oscar Wilde"), "Shakespeare wrote Hamlet around 1600."),
                BankItem("What is the smallest prime number?", "2", listOf("0", "1", "3"), "2 is the smallest prime and the only even prime."),
                BankItem("Which country has the largest land area?", "Russia", listOf("Canada", "China", "United States"), "Russia is the largest by area."),
            ),
            5 to listOf(
                BankItem("The Rosetta Stone helped scholars decipher which writing system?", "Egyptian hieroglyphs", listOf("Cuneiform", "Linear B", "Mayan glyphs"), "It carried the same decree in three scripts."),
                BankItem("What is the approximate value of pi to two decimal places?", "3.14", listOf("3.12", "3.41", "2.71"), "Pi ≈ 3.14159."),
                BankItem("Which of these is a prime number?", "89", listOf("87", "91", "93"), "89 is prime; 87 = 3×29, 91 = 7×13, 93 = 3×31."),
            ),
        ),
        "wellbeing" to mapOf(
            1 to listOf(
                BankItem("How many hours of sleep do most adults need each night?", "About 7–9", listOf("About 3–4", "About 11–12", "Only 5"), "Most adults do best with roughly 7–9 hours."),
                BankItem("Which helps your eyes after long screen use?", "Looking at something far away", listOf("Staring closer at the screen", "Turning brightness to the max", "Rubbing them hard"), "The 20-20-20 habit rests focusing muscles."),
                BankItem("What is a good first step when you feel overwhelmed?", "Take a few slow breaths", listOf("Check more notifications", "Skip a meal", "Stay up late"), "Slow breathing can settle the body."),
            ),
            2 to listOf(
                BankItem("The Pomodoro technique traditionally uses focus blocks of:", "25 minutes", listOf("5 minutes", "2 hours", "10 seconds"), "25 minutes, then a short break."),
                BankItem("Looking away from a screen every 20 minutes for 20 seconds at something 20 feet away is called the:", "20-20-20 rule", listOf("5-5-5 rule", "Rule of 3", "Half-time rule"), "It's a common eye-strain habit."),
                BankItem("Which is generally better before bed?", "Dim lights and fewer screens", listOf("Bright screens", "A large coffee", "A hard workout"), "Light and stimulation delay sleepiness."),
            ),
            3 to listOf(
                BankItem("About how long is one full sleep cycle?", "About 90 minutes", listOf("About 15 minutes", "About 3 hours", "About 6 hours"), "A typical cycle is roughly 90 minutes."),
                BankItem("Task switching usually makes work:", "Slower and more error-prone", listOf("Faster", "No different", "Easier to remember"), "Switching has a cost in time and accuracy."),
                BankItem("Which is a sign of 'sleep debt'?", "Needing caffeine to feel alert most days", listOf("Waking naturally before the alarm", "Feeling calm at night", "Dreaming vividly once"), "Chronic short sleep builds up."),
            ),
            4 to listOf(
                BankItem("What does HRV measure?", "Variation in time between heartbeats", listOf("Blood sugar", "Lung capacity", "Muscle strength"), "Heart rate variability is the variation between beats."),
                BankItem("'Implementation intentions' are plans written as:", "If situation X, then I will do Y", listOf("Maybe I will someday", "Always do everything", "Never do Z"), "If-then plans make follow-through easier."),
                BankItem("Which tends to boost focus for the next few hours?", "A short walk", listOf("Skipping water", "More tabs open", "Scrolling feeds"), "Brief movement can improve attention."),
            ),
            5 to listOf(
                BankItem("Which best describes 'time blindness'?", "Difficulty sensing how much time has passed", listOf("Being unable to see clocks", "Sleeping too long", "Always arriving early"), "Common in ADHD; external timers help."),
                BankItem("Why can a fixed start time make starting a task easier?", "It removes the decision about when to begin", listOf("It makes the task shorter", "It hides deadlines", "It skips planning"), "Fewer decisions means less friction."),
                BankItem("A screening questionnaire like the PHQ-9 can:", "Suggest whether to talk to a clinician", listOf("Diagnose depression", "Replace therapy", "Predict the future"), "Screeners guide next steps; they don't diagnose."),
            ),
        ),
)

/** Harder questions for levels 4 and 5. At those levels only these are used, never the easy ones. */
private val BANK_HARD: Map<String, Map<Int, List<BankItem>>> = mapOf(
    "language" to mapOf(
        4 to listOf(
            BankItem("The best antonym of 'ephemeral' is:", "Permanent", listOf("Fleeting", "Brief", "Ancient"), "Ephemeral means lasting a very short time; permanent is its opposite."),
            BankItem("'Obsequious' most nearly means:", "Excessively eager to please", listOf("Openly hostile", "Deeply curious", "Physically strong"), "Obsequious describes servile, over-eager flattery."),
            BankItem("Her argument was ___ and hard to refute.", "cogent", listOf("tepid", "frivolous", "vacuous"), "Cogent means clear, logical and convincing."),
        ),
        5 to listOf(
            BankItem("'Ascend up' is an example of:", "Redundancy", listOf("Metaphor", "Irony", "Alliteration"), "'Ascend' already means 'go up', so 'up' repeats it."),
            BankItem("'Sanguine' describes someone who is:", "Cheerfully optimistic", listOf("Extremely angry", "Very pale", "Easily frightened"), "Sanguine means hopeful and confident."),
        ),
    ),
    "science" to mapOf(
        4 to listOf(
            BankItem("Which gas makes up the largest share of Earth's atmosphere?", "Nitrogen", listOf("Oxygen", "Carbon dioxide", "Argon"), "Nitrogen is about 78% of dry air; oxygen is about 21%."),
            BankItem("The SI unit of electrical resistance is the:", "Ohm", listOf("Volt", "Ampere", "Watt"), "Resistance is measured in ohms (symbol Ω)."),
            BankItem("The speed of light in a vacuum is about:", "300,000 km per second", listOf("3,000 km per second", "30,000 km per second", "3,000,000 km per second"), "Light travels roughly 300,000 km every second."),
        ),
        5 to listOf(
            BankItem("Which element has the highest electronegativity?", "Fluorine", listOf("Oxygen", "Chlorine", "Nitrogen"), "Fluorine attracts electrons more strongly than any other element."),
            BankItem("How many chromosomes do typical human body cells contain?", "46", listOf("23", "44", "48"), "Humans have 23 pairs, so 46 chromosomes in most body cells."),
            BankItem("Avogadro's number, to two significant figures, is:", "6.0 × 10²³", listOf("6.0 × 10²⁶", "3.0 × 10²³", "6.0 × 10⁻²³"), "Avogadro's number is about 6.02 × 10²³ per mole."),
        ),
    ),
    "general" to mapOf(
        4 to listOf(
            BankItem("Which river is generally regarded as the longest in Africa?", "Nile", listOf("Congo", "Niger", "Zambezi"), "The Nile is usually counted as the longest river in Africa."),
            BankItem("In which year did the Berlin Wall fall?", "1989", listOf("1961", "1991", "1979"), "The wall was built in 1961 and opened in November 1989."),
        ),
        5 to listOf(
            BankItem("Who wrote 'The Brothers Karamazov'?", "Fyodor Dostoevsky", listOf("Leo Tolstoy", "Anton Chekhov", "Ivan Turgenev"), "Dostoevsky published it in 1880."),
            BankItem("Which conflict was ended by the Treaty of Westphalia in 1648?", "The Thirty Years' War", listOf("The Hundred Years' War", "The Seven Years' War", "The Napoleonic Wars"), "Westphalia ended the Thirty Years' War in Europe."),
        ),
    ),
)

private fun bankQuestion(rng: Random, category: String, level: Int): QuizQuestion {
    val byLevel = BANK[category] ?: BANK.getValue("general")
    val pool = BANK_HARD[category]?.get(level) ?: byLevel[level] ?: byLevel.getValue(1)
    val item = pickOne(rng, pool)
    val all = shuffled(rng, listOf(item.correct) + item.wrong)
    return QuizQuestion("", category, level, item.prompt, all, all.indexOf(item.correct), item.explain)
}

// ---------------------------------------------------------------- Public API

/** One question in [category] at [level] (1-5). Same seed and arguments give the same question. */
fun generateQuizQuestion(category: String, level: Int, rng: Random): QuizQuestion {
    val lv = level.coerceIn(1, QUIZ_MAX_LEVEL)
    val q = when (category) {
        "math" -> mathQuestion(rng, lv)
        "logic" -> logicQuestion(rng, lv)
        else -> bankQuestion(rng, category, lv)
    }
    return q.copy(id = "$category-$lv-${(rng.nextDouble() * 1e9).toInt()}")
}

/** Where today's gate starts: every two unlock attempts raise the starting level by one. */
fun baseLevelForAttempts(attemptsToday: Int): Int =
    minOf(QUIZ_MAX_LEVEL, 1 + maxOf(0, attemptsToday) / 2)

/**
 * A gate session: the user must answer [needed] questions correctly. Categories come from the
 * user's enabled list and rotate. Two right in a row moves difficulty up one level, a wrong
 * answer moves it down one (never below the day's base level). Own questions mix in one in four.
 */
class QuizSession(
    categories: List<String>,
    attemptsToday: Int = 0,
    val needed: Int = 3,
    seed: Long = System.currentTimeMillis(),
    private val customQuestions: List<CustomQuestion> = emptyList(),
    /** "category|prompt" keys already asked today (from the daily log): never asked again. */
    private val askedKeys: Set<String> = emptySet(),
    /** Per-category daily maximum; 0 or missing means no limit. */
    private val categoryCaps: Map<String, Int> = emptyMap(),
    /** How many questions of each category were already asked today. */
    private val usedToday: Map<String, Int> = emptyMap()
) {
    private val rng = Random(seed)
    val categories: List<String> =
        if (categories.isNotEmpty()) categories else QUIZ_CATEGORIES.map { it.id }
    val base: Int = baseLevelForAttempts(attemptsToday)
    var level: Int = base
        private set
    var correct: Int = 0
        private set
    var asked: Int = 0
        private set
    private var streak = 0
    private var last: QuizQuestion? = null
    private val askedThisSession = HashSet<String>()
    private val usedThisSession = HashMap<String, Int>()

    /** True when no fresh question is left in the chosen categories today. */
    var exhausted: Boolean = false
        private set

    val done: Boolean get() = correct >= needed

    private fun keyOf(category: String, prompt: String) = "$category|$prompt"

    private fun openCategories(): List<String> = categories.filter { c ->
        val cap = categoryCaps[c] ?: 0
        cap <= 0 || (usedToday[c] ?: 0) + (usedThisSession[c] ?: 0) < cap
    }

    /** The next question, or null when nothing fresh is left today (see [exhausted]). */
    fun next(): QuizQuestion? {
        val freshCustom = customQuestions.filter {
            val k = keyOf("custom", it.prompt)
            k !in askedKeys && k !in askedThisSession
        }
        if (freshCustom.isNotEmpty() && rng.nextDouble() < 0.25) {
            val c = pickOne(rng, freshCustom)
            askedThisSession.add(keyOf("custom", c.prompt))
            val q = QuizQuestion("custom-$asked", "custom", level, c.prompt, c.choices, c.answerIndex, c.explain)
            last = q
            return q
        }
        val open = openCategories()
        if (open.isEmpty()) {
            exhausted = true
            last = null
            return null
        }
        for (attempt in 0 until 40) {
            val cat = open[(asked + attempt) % open.size]
            val q = generateQuizQuestion(cat, level, rng)
            val key = keyOf(cat, q.prompt)
            if (key in askedKeys || key in askedThisSession) continue
            askedThisSession.add(key)
            usedThisSession[cat] = (usedThisSession[cat] ?: 0) + 1
            last = q
            return q
        }
        exhausted = true
        last = null
        return null
    }

    fun answer(index: Int): QuizAnswerResult {
        val q = last
        val right = q != null && index == q.answerIndex
        asked++
        if (right) {
            correct++
            streak++
            if (streak >= 2) {
                level = minOf(QUIZ_MAX_LEVEL, level + 1)
                streak = 0
            }
        } else {
            streak = 0
            level = maxOf(base, level - 1)
        }
        return QuizAnswerResult(right, q?.explain ?: "", q?.answerIndex ?: -1, done)
    }
}
