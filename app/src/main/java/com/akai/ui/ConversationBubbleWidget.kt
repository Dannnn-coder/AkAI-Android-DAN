package com.akai.ui

import android.content.Context
import android.widget.LinearLayout
import android.graphics.Color
import com.akai.data.ConversationEntry
import com.akai.data.SenderType

abstract class ConversationBubbleWidget(context: Context) : LinearLayout(context) {

    abstract fun render(entry: ConversationEntry)

    companion object {
        fun readableTextColor(backgroundColor: Int): Int {
            val luminance = 0.299 * Color.red(backgroundColor) +
                0.587 * Color.green(backgroundColor) +
                0.114 * Color.blue(backgroundColor)
            return if (luminance > 170) Color.BLACK else Color.WHITE
        }

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
