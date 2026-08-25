package com.akai.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

enum class SenderType { DEAF, HEARING }

data class ConversationEntry(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val sender: SenderType,
    val timestamp: String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
) {
    /**
     * Serialize this entry to a compact JSON string so it can be sent to the
     * paired device over Nearby Connections. Only conversation TEXT crosses the
     * wire — never camera frames, audio, or any personal data (RA 10173).
     */
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("sender", sender.name)
        put("timestamp", timestamp)
    }.toString()

    companion object {
        /**
         * Rebuild a ConversationEntry from JSON received from the paired device.
         * The original id/sender/timestamp are preserved so a remote message
         * renders identically on both phones (same bubble side, same time).
         * Returns null if the payload is malformed, so a bad packet can't crash
         * the conversation.
         */
        fun fromJson(json: String): ConversationEntry? = try {
            val obj = JSONObject(json)
            ConversationEntry(
                id = obj.getString("id"),
                text = obj.getString("text"),
                sender = SenderType.valueOf(obj.getString("sender")),
                timestamp = obj.getString("timestamp")
            )
        } catch (e: Exception) {
            null
        }
    }
}
