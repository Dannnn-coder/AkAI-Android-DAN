package com.akai.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

enum class SenderType { DEAF, HEARING }

data class ConversationEntry(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val sender: SenderType,
    val timestamp: String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
    /**
     * True when this entry was created on THE CURRENT device, false when it was
     * received from the paired device. Used to decide bubble alignment during an
     * active Dual Device session (local -> RIGHT, remote -> LEFT).
     *
     * This is device-specific metadata, so it is deliberately NOT serialized —
     * each device tags its own entries. When we receive an entry from the peer we
     * mark a copy as local=false.
     */
    val local: Boolean = true
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
         * renders identically on both phones (same time). The [local] flag is
         * defaulted to true here; the caller marks received entries as remote.
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
