package com.akai.ui

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.TextView
import com.akai.R
import com.akai.data.ConversationEntry

class DeafBubbleWidget(
    context: Context,
    private val onSpeak: ((String) -> Unit)?,
    private val bubbleColor: Int?
) : ConversationBubbleWidget(context) {

    init {
        LayoutInflater.from(context).inflate(R.layout.item_message_deaf, this, true)
    }

    override fun render(entry: ConversationEntry) {
        val messageView = findViewById<TextView>(R.id.tvMessage)
        messageView.text = entry.text
        bubbleColor?.let {
            messageView.background.mutate().setTint(it)
            messageView.setTextColor(readableTextColor(it))
        }
        findViewById<TextView>(R.id.tvTimestamp).text = entry.timestamp
        findViewById<ImageButton>(R.id.btnSpeakMessage).setOnClickListener {
            onSpeak?.invoke(entry.text)
        }
    }

    private fun readableTextColor(backgroundColor: Int): Int {
        val luminance = 0.299 * Color.red(backgroundColor) +
            0.587 * Color.green(backgroundColor) +
            0.114 * Color.blue(backgroundColor)
        return if (luminance > 170) Color.BLACK else Color.WHITE
    }
}
