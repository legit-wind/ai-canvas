package com.aicanvas.app.data

/**
 * The memory model. Every fact is kept on the phone and shown in Settings → Memories, but only the most
 * relevant ones go to the AI with each message, so the prompt stays small as memory grows.
 * Relevance = shared words with the message, then the newest first. Pure and unit-tested.
 */
object MemoryRank {
    const val LIMIT = 15
    private val stop = setOf("the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "is", "are", "my", "me", "i",
        "user", "user's", "their", "they", "with", "at", "it", "this", "that", "be", "has", "have", "add", "make", "show")

    fun words(text: String): Set<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 && it !in stop }
            .map { it.removeSuffix("s") }.toSet()

    fun pick(memories: List<Memory>, message: String, limit: Int = LIMIT): List<Memory> {
        if (memories.size <= limit) return memories
        val asked = words(message)
        return memories.sortedWith(compareByDescending<Memory> { (words(it.text) intersect asked).size }.thenByDescending { it.createdAt })
            .take(limit)
    }

    /** True when [fact] says the same as an existing memory, ignoring case, punctuation and word order. */
    fun isDuplicate(fact: String, memories: List<Memory>): Boolean {
        val w = words(fact)
        return w.isNotEmpty() && memories.any { words(it.text) == w }
    }
}
