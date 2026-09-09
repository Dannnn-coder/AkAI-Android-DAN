package com.akai.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.akai.R

/**
 * Full-screen blue "Connected to Session" page shown once a Dual Device session
 * is successfully established. It shows the real session code and a single action
 * button whose label/callback depends on the role:
 *
 *   - Host   -> "End Session"  ([onAction])
 *   - Joiner -> "Disconnect"   ([onAction])
 *
 * plus a circular X exit ([onExit]). Both [onAction] and [onExit] are expected to
 * properly tear down the session before invoking the return transition.
 *
 * The page slides up on arrival and can slide back down (used for the return-to-
 * Main-Activity transition) or be dismissed instantly ([showNow]/[dismissNow]).
 */
class ConnectedSessionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val titleView: TextView by lazy { findViewById(R.id.tvConnectedTitle) }
    private val codeView: TextView by lazy { findViewById(R.id.tvConnectedCode) }
    private val actionView: TextView by lazy { findViewById(R.id.btnConnectedAction) }
    private val exitView: ImageButton by lazy { findViewById(R.id.btnConnectedExit) }

    /** Invoked when the main action (End Session / Disconnect) is pressed. */
    var onAction: () -> Unit = {}
    /** Invoked when the X button is pressed. */
    var onExit: () -> Unit = {}

    private var isExiting = false

    init {
        inflate(context, R.layout.view_connected_session, this)
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true

        // Keep the X button clear of the navigation / gesture bar on edge-to-edge phones.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            (exitView.layoutParams as? MarginLayoutParams)?.bottomMargin = dp(40f) + navBottom
            insets
        }

        actionView.setOnClickListener { onAction() }
        exitView.setOnClickListener { onExit() }

        // Park below the window so enter() physically slides the page up.
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

    /** Set the session code text (e.g. "AK-4829"). */
    fun setCode(code: String) {
        codeView.text = code
    }

    /** Configure the main action button for the current role.
     *  [label] is "End Session" (host) or "Disconnect" (joiner). */
    fun setActionLabel(label: String) {
        actionView.text = label
    }

    /** Slides the page up from the bottom edge until it fully covers the window. */
    fun enter(onCovered: (() -> Unit)? = null) {
        translationY = fullHeightPx()
        animate()
            .translationY(0f)
            .setDuration(440L)
            .setInterpolator(DecelerateInterpolator(2f))
            .withEndAction { onCovered?.invoke() }
            .start()
    }

    /** Slides the page down off the bottom edge, then detaches it.
     *  Used ONLY after the session has been cleaned up (the X / End / Disconnect
     *  return-to-Main transition). */
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

    /** Shows the page immediately without any transition. */
    fun showNow() {
        animate().cancel()
        translationY = 0f
    }

    /** Detaches the page immediately without any transition. */
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
