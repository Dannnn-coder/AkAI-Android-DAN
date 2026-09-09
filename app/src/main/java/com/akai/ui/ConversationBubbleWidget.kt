package com.akai.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.akai.R
import com.akai.data.ConversationEntry
import com.akai.data.SenderType

/** Which side of the conversation row a bubble occupies. */
enum class BubbleSide {
    LEFT,
    RIGHT
}

/**
 * Unified conversational message bubble that supports BOTH sides and BOTH speaker
 * styles from one layout ([R.layout.item_message_bubble]):
 *
 *  - Accessible color/readability preserved from the old Deaf/Hearing widgets.
 *  - THE SENDER's avatar (D or H) is placed on the message's OUTER edge, whichever
 *    side it is on; the bubble tail points toward the avatar.
 *  - In single-device mode side is fixed by speaker (Deaf=LEFT, Hearing=RIGHT) so
 *    the conversation looks exactly as before.
 *  - In a Dual Device session the side comes from message ORIGIN (own=RIGHT,
 *    remote=LEFT) regardless of speaker, so each phone shows its own messages on
 *    the right.
 */
class ConversationBubbleWidget(context: Context) : LinearLayout(context) {

    private val avatarDeaf: TextView by lazy { findViewById(R.id.avatarDeaf) }
    private val avatarHearing: TextView by lazy { findViewById(R.id.avatarHearing) }
    private val messageView: TextView by lazy { findViewById(R.id.tvMessage) }
    private val timestampView: TextView by lazy { findViewById(R.id.tvTimestamp) }
    private val speakButton: ImageButton by lazy { findViewById(R.id.btnSpeakMessage) }
    private val row: LinearLayout by lazy { findViewById(R.id.bubbleRow) }
    private val bubbleUnit: LinearLayout by lazy { findViewById(R.id.bubbleUnit) }

    private var onSpeak: ((String) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.item_message_bubble, this, true)
    }

    private fun px(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    /** The bubble's visible speaker/audio button (only Deaf/FSL messages show one),
     *  used as the point-and-click "Audio" Help target; null when it is not on screen. */
    fun audioButtonOrNull(): ImageButton? =
        if (speakButton.visibility == VISIBLE) speakButton else null

    fun render(entry: ConversationEntry, side: BubbleSide, onDeafMessageSpeak: ((String) -> Unit)?, deafBubbleColor: Int?, hearingBubbleColor: Int?) {
        messageView.text = entry.text
        timestampView.text = entry.timestamp
        onSpeak = onDeafMessageSpeak

        val isDeaf = entry.sender == SenderType.DEAF
        val bubbleColor = if (isDeaf) deafBubbleColor else hearingBubbleColor
        bubbleColor?.let {
            messageView.background = bubble3dBackground(it, tailOnStart = side == BubbleSide.LEFT, density = resources.displayMetrics.density)
            messageView.setTextColor(readableTextColor(it))
        }
        if (isDeaf) {
            // Blue "D" avatar in the sender color lane; only Deaf messages read aloud.
            avatarDeaf.visibility = VISIBLE
            avatarHearing.visibility = GONE
            speakButton.visibility = VISIBLE
            speakButton.setOnClickListener { onSpeak?.invoke(entry.text) }

            // Audio button placement depends on the message's origin/side:
            //  - LEFT (single-device Deaf, remote Deaf): [profile][sentence][audio]
            //  - RIGHT (local Deaf in a Dual Device session): [audio][sentence][profile]
            val speakDesiredIndex = if (side == BubbleSide.RIGHT) 0 else bubbleUnit.childCount - 1
            val speakCurrentIndex = bubbleUnit.indexOfChild(speakButton)
            if (speakCurrentIndex != speakDesiredIndex) {
                bubbleUnit.removeView(speakButton)
                bubbleUnit.addView(speakButton, speakDesiredIndex)
            }
            // When the audio button precedes the sentence it needs an end gap instead
            // of the default start gap.
            (speakButton.layoutParams as? MarginLayoutParams)?.let { p ->
                if (side == BubbleSide.RIGHT) {
                    p.marginStart = 0
                    p.marginEnd = px(6f)
                } else {
                    p.marginStart = px(6f)
                    p.marginEnd = 0
                }
                speakButton.layoutParams = p
            }
        } else {
            avatarHearing.visibility = VISIBLE
            avatarDeaf.visibility = GONE
            speakButton.visibility = GONE
            speakButton.setOnClickListener(null)
        }

        // The row fills the conversation width so the text column can never overflow
        // the visible area; content is hugged to the requested side instead.
        row.gravity = if (side == BubbleSide.LEFT) Gravity.START else Gravity.END

        // Anchor the widget on the requested side of the vertical conversation row.
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        layoutParams = lp

        // The sender avatar must sit on the OUTER edge of the message block. It
        // belongs to [row] (the inflated message row), so it must be reordered
        // WITHIN that row — never on this widget (that would throw, as an avatar is
        // not a direct child of the widget).
        val headAvatar = if (isDeaf) avatarDeaf else avatarHearing
        val shouldPrecedeBubble = side == BubbleSide.LEFT
        val desiredIndex = if (shouldPrecedeBubble) 0 else row.childCount - 1
        val currentIndex = row.indexOfChild(headAvatar)
        if (currentIndex != desiredIndex && desiredIndex >= 0) {
            row.removeView(headAvatar)
            row.addView(headAvatar, desiredIndex)
        }
    }

    companion object {
        const val DEFAULT_DEAF_BUBBLE_COLOR: Int = 0xFF5796DB.toInt()
        const val DEFAULT_HEARING_BUBBLE_COLOR: Int = 0xFFFFFFFF.toInt()

        fun readableTextColor(backgroundColor: Int): Int {
            val luminance = 0.299 * Color.red(backgroundColor) +
                0.587 * Color.green(backgroundColor) +
                0.114 * Color.blue(backgroundColor)
            return if (luminance > 170) Color.BLACK else Color.WHITE
        }

        fun create(
            context: Context,
            entry: ConversationEntry,
            side: BubbleSide,
            onDeafMessageSpeak: ((String) -> Unit)? = null,
            deafBubbleColor: Int? = null,
            hearingBubbleColor: Int? = null
        ): ConversationBubbleWidget {
            return ConversationBubbleWidget(context).apply {
                render(entry, side, onDeafMessageSpeak, deafBubbleColor, hearingBubbleColor)
            }
        }
    }
}

/** Builds the layered "3D" bubble background: main color on top of a darker depth layer. */
fun bubble3dBackground(mainColor: Int, tailOnStart: Boolean, density: Float): Drawable {
    val mainRadius = 20f * density
    val tailRadius = 8f * density
    val inset = (3f * density).toInt()
    val radius = floatArrayOf(
        if (tailOnStart) tailRadius else mainRadius,
        if (tailOnStart) tailRadius else mainRadius,
        if (tailOnStart) mainRadius else tailRadius,
        if (tailOnStart) mainRadius else tailRadius,
        mainRadius, mainRadius,
        mainRadius, mainRadius
    )
    val shadow = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(shadowOf(mainColor))
        cornerRadii = radius
    }
    val main = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(mainColor)
        cornerRadii = radius
    }
    val layers = LayerDrawable(arrayOf(shadow, main))
    layers.setLayerInset(0, 0, inset, 0, 0)
    layers.setLayerInset(1, 0, 0, 0, inset)
    return layers
}

private fun shadowOf(mainColor: Int): Int {
    if (mainColor == Color.parseColor("#5796DB")) return Color.parseColor("#4A6FCC")
    if (mainColor == Color.parseColor("#FFFFFF")) return Color.parseColor("#9FB3E4")
    return Color.rgb(
        (Color.red(mainColor) * 0.82).toInt(),
        (Color.green(mainColor) * 0.82).toInt(),
        (Color.blue(mainColor) * 0.82).toInt()
    )
}