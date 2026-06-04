package com.akai.ui

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import com.akai.R
import com.akai.data.ConversationEntry

class HearingBubbleWidget(
    context: Context,
    private val bubbleColor: Int?
) : ConversationBubbleWidget(context) {

    init {
        LayoutInflater.from(context).inflate(R.layout.item_message_hearing, this, true)
    }

    override fun render(entry: ConversationEntry) {
        val messageView = findViewById<TextView>(R.id.tvMessage)
        messageView.text = entry.text
        bubbleColor?.let {
            messageView.background.mutate().setTint(it)
            messageView.setTextColor(readableTextColor(it))
        }
        findViewById<TextView>(R.id.tvTimestamp).text = entry.timestamp
    }
}
