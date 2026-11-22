package com.focuslens

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

    /** Three questions from three different categories, in a random order. */
    fun pickThree(): List<InterceptQuestion> {
        val groups = all.groupBy { it.category }.values.map { it.shuffled() }.shuffled()
        return groups.take(3).map { it.first() }
    }
}
