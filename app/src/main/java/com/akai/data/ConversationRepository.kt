package com.akai.data

class ConversationRepository {
    private val _entries = mutableListOf<ConversationEntry>()

    fun addEntry(entry: ConversationEntry) {
        _entries.add(entry)
    }

    fun getAll(): List<ConversationEntry> = _entries.toList()

    fun clear() {
        _entries.clear()
    }
}
