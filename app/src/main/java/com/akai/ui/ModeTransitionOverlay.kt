package com.akai.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import com.akai.R

/**
 * Full-screen blue panel that covers the whole window during Camera <-> Voice
 * switches.
 *
 * Sequence driven by MainActivity:
 *  1. [enter] — slides the panel up from below the window (ease-out) and lets
 *     the centered title fade in over the arriving panel.
 *  2. The custom bar flows while the new mode actually initializes.
 *  3. [complete] — bar glides to 100%.
 *  4. [exit] — the panel slides upward off-screen (ease-in) revealing the mode.
 *
 * Clickable + focusable so it swallows touches while it covers the UI.
 */
class ModeTransitionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val bar: ModeTransitionBar
    private val content: View
    private var completed = false

    init {
        inflate(context, R.layout.view_mode_transition, this)
        bar = findViewById(R.id.transitionBar)
        content = findViewById(R.id.transitionContent)
        isClickable = true
        isFocusable = true
        // Park the panel fully BELOW the window so enter() physically slides it up.
        translationY = fullHeightPx()
    }

    fun setTitle(title: String) {
        findViewById<TextView>(R.id.tvTransitionTitle).text = title
    }

    /** Slides the panel up onto the screen and fades the title in on top of it.
     *  [onCovered] fires the moment the panel is completely over the window —
     *  only then should the caller actually switch the underlying mode. */
    fun enter(onCovered: (() -> Unit)? = null) {
        content.alpha = 0f
        content.translationY = dp(18f)
        content.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(340L)
            .setStartDelay(170L)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()

        animate()
            .translationY(0f)
            .setDuration(430L)
            .setInterpolator(DecelerateInterpolator(2f))
            .withEndAction { onCovered?.invoke() }
            .start()

        bar.startPulsing()
    }

    /** Stops the flowing bar once the new mode is ready; safe to call once. */
    fun complete(onDone: () -> Unit) {
        if (completed) return
        completed = true
        bar.finish(onDone)
    }

    /** Slides the panel upward and off the top of the screen. */
    fun exit(onDone: () -> Unit) {
        animate()
            .translationY(-fullHeightPx())
            .setDuration(420L)
            .setInterpolator(AccelerateInterpolator(1.7f))
            .withEndAction { onDone() }
            .start()
    }

    private fun fullHeightPx(): Float = resources.displayMetrics.heightPixels.toFloat()

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}