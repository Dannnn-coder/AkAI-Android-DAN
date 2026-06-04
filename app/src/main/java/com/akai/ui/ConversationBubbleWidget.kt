package com.akai.ui

import android.content.Context
import android.widget.LinearLayout
import com.akai.data.ConversationEntry
import com.akai.data.SenderType

abstract class ConversationBubbleWidget(context: Context) : LinearLayout(context) {

    abstract fun render(entry: ConversationEntry)

    companion object {
        fun create(
            context: Context,
            entry: ConversationEntry,
            onDeafMessageSpeak: ((String) -> Unit)? = null,
            deafBubbleColor: Int? = null,
            hearingBubbleColor: Int? = null
        ): ConversationBubbleWidget {
            return when (entry.sender) {
                SenderType.DEAF    -> DeafBubbleWidget(context, onDeafMessageSpeak, deafBubbleColor).apply { render(entry) }
                SenderType.HEARING -> HearingBubbleWidget(context, hearingBubbleColor).apply { render(entry) }
            }
        }
    }
}
