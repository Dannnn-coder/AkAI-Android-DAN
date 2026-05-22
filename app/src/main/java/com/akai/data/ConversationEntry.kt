package com.akai.data

import java.text.SimpleDateFormat
import java.util.*

enum class SenderType { DEAF, HEARING }

data class ConversationEntry(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val sender: SenderType,
    val timestamp: String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
)
