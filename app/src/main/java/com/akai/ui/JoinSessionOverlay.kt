package com.akai.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.akai.R

/**
 * Full-screen blue Join Session page (part of the Dual Device flow).
 *
 * The "AK" prefix is a frozen label; only the four digit slots accept input,
 * with automatic focus advance and backspace-return. [collectCode] rebuilds the
 * real "AK-XXXX" code and [onJoin] hands it to the EXISTING joinSession flow.
 */
class JoinSessionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val slots by lazy { listOf(
        findViewById<EditText>(R.id.inputCode0),
        findViewById<EditText>(R.id.inputCode1),
        findViewById<EditText>(R.id.inputCode2),
        findViewById<EditText>(R.id.inputCode3)
    ) }
    private val statusView: TextView by lazy { findViewById(R.id.tvJoinStatus) }
    private val loadingBar: LanguageLoadingBar by lazy { findViewById(R.id.joinLoadingIndicator) }
    private val bottomRow: View by lazy { findViewById(R.id.joinBottomRow) }
    private val btnCancel: View by lazy { findViewById(R.id.btnJoinCancel) }
    private val btnSubmit: View by lazy { findViewById(R.id.btnJoinSubmit) }

    /** Invoked by Cancel / Join — actual session work happens in MainActivity. */
    var onCancel: () -> Unit = {}
    var onJoin: () -> Unit = {}

    private var isExiting = false

    /** Exposes the Join button so MainActivity can enable/disable it while searching. */
    val joinButton: View get() = btnSubmit

    init {
        inflate(context, R.layout.view_join_session, this)
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true

        // Keep Cancel / Join clear of the navigation / gesture bar on edge-to-edge phones.
        // The window uses adjustNothing (see manifest), so the IME never resizes the
        // page — the buttons stay at the real screen bottom whether the keyboard is
        // open or closed, and the keyboard simply overlays them. We only add the nav-bar
        // inset so the buttons clear the gesture/navigation bar.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            (bottomRow.layoutParams as? MarginLayoutParams)?.bottomMargin = dp(24f) + nav
            insets
        }

        configureSlots()

        btnCancel.setOnClickListener {
            hideKeyboard()
            onCancel()
        }
        btnSubmit.setOnClickListener {
            hideKeyboard()
            onJoin()
        }

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

    private fun configureSlots() {
        slots.forEachIndexed { index, slot ->
            slot.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    // Advance to the next slot once this one fills. Only move the
                    // cursor within the next slot's EXISTING text length — the next
                    // slot is normally empty, and setSelection(1) on an empty field
                    // throws IndexOutOfBoundsException (the old crash on first digit).
                    if (s?.length == 1 && index < slots.lastIndex) {
                        val next = slots[index + 1]
                        next.requestFocus()
                        next.setSelection(next.text.length)
                    }
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
            if (index == slots.lastIndex) {
                slot.imeOptions = EditorInfo.IME_ACTION_DONE
                slot.setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        // Just dismiss the keyboard — do NOT trigger onJoin() here.
                        // The user must tap the actual Join button to submit.
                        slot.clearFocus()
                        hideKeyboard()
                        true
                    } else false
                }
            } else {
                slot.imeOptions = EditorInfo.IME_ACTION_NEXT
            }
            slot.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN
                    && slot.text.isEmpty() && index > 0
                ) {
                    val previous = slots[index - 1]
                    previous.requestFocus()
                    previous.text.clear()
                    true
                } else {
                    false
                }
            }
        }
    }

    /** "AK-XXXX" if all four slots are filled, otherwise null (focuses the first empty). */
    fun collectCode(): String? {
        val chars = slots.joinToString("") { it.text.toString().trim() }
        if (chars.length == 4) return "AK-${chars.uppercase()}"
        slots.firstOrNull { it.text.isEmpty() }?.requestFocus()
        return null
    }

    fun setStatusText(text: String?) {
        statusView.text = text ?: ""
        statusView.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    /** Shows the white loading bar below the status text. */
    fun showLoading() {
        loadingBar.visibility = View.VISIBLE
        loadingBar.start()
    }

    /** Hides the loading bar (called on CONNECTED, ERROR, or Cancel). */
    fun hideLoading() {
        loadingBar.stop()
        loadingBar.visibility = View.GONE
    }

    fun clearSlots() {
        slots.forEach { it.text.clear() }
        slots.first().requestFocus()
    }

    private fun hideKeyboard() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(windowToken, 0)
    }

    /** Slides the page up from the bottom edge until it fully covers the window. */
    fun enter(onCovered: (() -> Unit)? = null) {
        translationY = fullHeightPx()
        animate()
            .translationY(0f)
            .setDuration(440L)
            .setInterpolator(DecelerateInterpolator(2f))
            .withEndAction {
                onCovered?.invoke()
                slots.first().requestFocus()
            }
            .start()
    }

    /** Slides the page down off the bottom edge, then detaches it.
     *  Used ONLY for the successful-join transition (kept on purpose). */
    fun exit(onDone: () -> Unit) {
        if (isExiting) return
        isExiting = true
        hideKeyboard()
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

    /** Shows the page immediately (no slide) and hands focus to the first slot. */
    fun showNow() {
        animate().cancel()
        translationY = 0f
        slots.first().requestFocus()
    }

    /** Detaches the page immediately without any transition (used on Cancel). */
    fun dismissNow() {
        if (isExiting) return
        isExiting = true
        hideKeyboard()
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