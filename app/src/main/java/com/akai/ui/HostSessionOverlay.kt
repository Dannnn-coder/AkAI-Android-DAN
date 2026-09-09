package com.akai.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.akai.R

/**
 * Full-screen blue Host Session page (part of the Dual Device flow).
 *
 * The session code comes from the existing generator (set via [setCode]) and the
 * real connection state decides when waiting ends — no fake timer. Cancel slides
 * the page down and MainActivity stops the session through the existing
 * endSession() flow. Clickable + focusable so MainActivity never receives touches.
 */
class HostSessionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val codeView: TextView by lazy { findViewById(R.id.tvHostCode) }
    private val waitingView: TextView by lazy { findViewById(R.id.tvHostWaiting) }
    private val loadingBar: LanguageLoadingBar by lazy { findViewById(R.id.hostLoadingIndicator) }
    private val btnCancel: View by lazy { findViewById(R.id.btnHostCancel) }

    /** Invoked when Cancel is pressed (session teardown handled in MainActivity). */
    var onCancel: () -> Unit = {}

    private var isExiting = false

    init {
        inflate(context, R.layout.view_host_session, this)
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true

        // Keep Cancel clear of the navigation / gesture bar on edge-to-edge phones.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            (btnCancel.layoutParams as? MarginLayoutParams)?.bottomMargin = dp(40f) + navBottom
            insets
        }

        btnCancel.setOnClickListener { onCancel() }

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

    fun setCode(code: String) {
        codeView.text = code
    }

    fun setWaitingText(text: String) {
        waitingView.text = text
    }

    /** Shows the white loading bar below the waiting text. */
    fun showLoading() {
        loadingBar.visibility = View.VISIBLE
        loadingBar.start()
    }

    /** Hides the loading bar (called on CONNECTED or ERROR). */
    fun hideLoading() {
        loadingBar.stop()
        loadingBar.visibility = View.GONE
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
     *  Used ONLY for the connect success transition (kept on purpose). */
    fun exit(onDone: () -> Unit) {
        if (isExiting) return
        isExiting = true
        loadingBar.stop()
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

    /** Detaches the page immediately without any transition (used on Cancel). */
    fun dismissNow() {
        if (isExiting) return
        isExiting = true
        loadingBar.stop()
        animate().cancel()
        (parent as? ViewGroup)?.removeView(this)
    }

    private fun fullHeightPx(): Float {
        val parentHeight = (parent as? View)?.height
        return (parentHeight ?: resources.displayMetrics.heightPixels).toFloat()
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()
}