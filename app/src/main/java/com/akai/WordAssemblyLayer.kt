package com.akai

class WordAssemblyLayer {

    private val buffer = mutableListOf<String>()
    private var lastAddTime = 0L
    private val MIN_LETTER_GAP_MS = 800L

    fun addLetter(letter: String): Boolean {
        val now = System.currentTimeMillis()
        // Deduplication — prevent same letter flooding buffer
        if (letter == buffer.lastOrNull() && (now - lastAddTime) < MIN_LETTER_GAP_MS) {
            return false
        }
        buffer.add(letter)
        lastAddTime = now
        return true
    }

    fun confirm(): String {
        val word = buffer.joinToString("")
        buffer.clear()
        return word
    }

    fun clear() {
        buffer.clear()
    }

    fun getBuffer(): List<String> = buffer.toList()

    fun isEmpty(): Boolean = buffer.isEmpty()
}
