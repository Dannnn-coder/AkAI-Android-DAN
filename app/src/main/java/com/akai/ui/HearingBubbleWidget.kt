package com.akai.ui

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import com.akai.R
import com.akai.data.ConversationEntry

class HearingBubbleWidget(context: Context) : ConversationBubbleWidget(context) {

    init {
        LayoutInflater.from(context).inflate(R.layout.item_message_hearing, this, true)
    }

    override fun render(entry: ConversationEntry) {
        findViewById<TextView>(R.id.tvMessage).text = entry.text
        findViewById<TextView>(R.id.tvTimestamp).text = entry.timestamp
    }
}
