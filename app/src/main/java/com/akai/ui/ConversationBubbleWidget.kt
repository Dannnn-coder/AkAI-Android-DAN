package com.akai.ui

import android.content.Context
import android.widget.LinearLayout
import com.akai.data.ConversationEntry
import com.akai.data.SenderType

abstract class ConversationBubbleWidget(context: Context) : LinearLayout(context) {

    abstract fun render(entry: ConversationEntry)

    companion object {
        fun create(context: Context, entry: ConversationEntry): ConversationBubbleWidget {
            return when (entry.sender) {
                SenderType.DEAF    -> DeafBubbleWidget(context).apply { render(entry) }
                SenderType.HEARING -> HearingBubbleWidget(context).apply { render(entry) }
            }
        }
    }
}
