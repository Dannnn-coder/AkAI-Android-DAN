package com.akai.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.akai.R

/**
 * Full-screen blue Dual Device screen that slides up over MainActivity.
 *
 *  - [enter] rises from below the window (ease-out) until it fully covers the UI.
 *  - [exit] slides back down off the bottom edge (ease-in), exactly reversing the
 *    entrance, then removes itself from its parent.
 *
 * Host / Join still run the existing session flow (theirs is wired in MainActivity);
 * this class only owns the layout and the slide. Clickable + focusable so the screen
 * swallows all touches while open — MainActivity underneath never receives them.
 */
class DualDeviceOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Host / Join are LinearLayouts (whole button is clickable). */
    val btnHost: View by lazy { findViewById(R.id.btnDualHost) }
    val btnJoin: View by lazy { findViewById(R.id.btnDualJoin) }
    private val btnExit: ImageButton by lazy { findViewById(R.id.btnDualExit) }

    /** Invoked when the user closes the screen (kept pessimistic on purpose). */
    var onExit: () -> Unit = {}

    private var isExiting = false

    init {
        inflate(context, R.layout.view_dual_device, this)
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true

        // Keep Exit clear of the navigation / gesture bar on edge-to-edge phones:
        // a tall base gap plus the actual nav inset, so it is never partially covered.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            (btnExit.layoutParams as? MarginLayoutParams)?.bottomMargin = dp(40f) + navBottom
            insets
        }

        btnExit.setOnClickListener { onExit() }

        // Park below the window so enter() physically slides the screen up.
        translationY = fullHeightPx()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        paintSystemBarsForOverlay()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        restoreSystemBarsForOverlay()
    }

    /** During an active session the bar's Start/Join buttons hide; mirror that here. */
    fun setInSession(inSession: Boolean) {
        btnHost.visibility = if (inSession) View.GONE else View.VISIBLE
        btnJoin.visibility = if (inSession) View.GONE else View.VISIBLE
    }

    /** Slides the screen up from the bottom edge until it fully covers the window. */
    fun enter(onCovered: (() -> Unit)? = null) {
        translationY = fullHeightPx()
        animate()
            .translationY(0f)
            .setDuration(440L)
            .setInterpolator(DecelerateInterpolator(2f))
            .withEndAction { onCovered?.invoke() }
            .start()
    }

    /** Slides the screen down off the bottom edge, then detaches it.
     *  Also reused as the transition on a successful join. */
    fun exit(onDone: () -> Unit) {
        if (isExiting) return
        isExiting = true
        animate()
            .translationY(fullHeightPx())
            .setDuration(400L)
            .setInterpolator(AccelerateInterpolator(2f))
            .withEndAction {
                onDone()
                (parent as? ViewGroup)?.removeView(this)
            }
            .start()
    }

    /** Shows the screen immediately without any transition (page-to-page jumps). */
    fun showNow() {
        animate().cancel()
        translationY = 0f
    }

    /** Detaches the screen immediately without any transition. */
    fun dismissNow() {
        if (isExiting) return
        isExiting = true
        animate().cancel()
        (parent as? ViewGroup)?.removeView(this)
    }

    private fun fullHeightPx(): Float {
        val parentHeight = (parent as? View)?.height
        return (parentHeight ?: resources.displayMetrics.heightPixels).toFloat()
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()
}