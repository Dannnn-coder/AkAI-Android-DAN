package com.akai.ui

import android.animation.ObjectAnimator
import android.app.Activity
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.akai.R

/**
 * THE shared stable overlay used by BOTH the point-and-click Help and the
 * first-time tutorial. Architecture:
 *
 *  - Starts with the REAL screen visible under a LIGHT dim and the subtle
 *    instruction "Select a component" centered (no big title, no cards/list).
 *  - The overlay consumes every touch, so the real application can NEVER run
 *    while it is active. In Help mode a touch is hit-tested against the ACTUAL
 *    component views (their real screen bounds); in Tutorial mode only the
 *    Skip/Back/Next/Got It! buttons work.
 *  - Selecting a component moves the spotlight to that component's real bounds,
 *    darkens the rest, and swaps the stable centered caption to the component's
 *    friendly description.
 *  - The caption sits in a fixed centered container with FIXED sub-region heights,
 *    so it NEVER jumps or resizes between steps; only the spotlight moves.
 *  - Tutorial step 5 (conversation) animates the caption UP (ease-in/out) to reveal
 *    the conversation; step 6 animates it back toward the center, with "Got It!".
 *  - The circular blue 3D X close button sits at the middle-right and always works.
 */
class ContextualHelpOverlay(
    private val activity: Activity,
    private val targets: List<HelpTarget>,
    private val tutorial: Boolean = false,
    private val onExit: () -> Unit
) {
    data class HelpTarget(val view: View, val title: String, val description: String)

    private lateinit var root: FrameLayout
    private lateinit var spotlight: SpotlightView
    private lateinit var caption: SpotlightCaptionBox
    private lateinit var centerHost: FrameLayout
    private lateinit var instructionText: TextView
    private lateinit var btnSkip: TextView
    private lateinit var btnBack: TextView
    private lateinit var btnNext: TextView

    private var currentIndex = 0
    private var captionY = 0f
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private val density: Float get() = activity.resources.displayMetrics.density

    // LIGHT dim while Help waits for a click (the real screen stays clearly
    // visible); DARK dim once a component is highlighted or during the tutorial.
    private val lightDim = Color.parseColor("#59000000")
    private val darkDim = Color.parseColor("#CC000000")

    fun start() {
        if (targets.isEmpty()) {
            onExit()
            return
        }
        val contentRoot = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)

        root = FrameLayout(activity).apply {
            // Touch sink: every touch is consumed here — nothing behind can react,
            // and taps on empty/dimmed areas do nothing.
            setOnTouchListener { v, event -> handleTouch(v, event); true }
            setBackgroundColor(Color.TRANSPARENT)
        }

        spotlight = SpotlightView(activity).apply { setDimColor(lightDim) }
        root.addView(spotlight, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        caption = SpotlightCaptionBox(activity)
        if (tutorial) {
            caption.hideEyebrow()
            caption.showActions()
            buildActions()
        } else {
            caption.hideEyebrow()
            caption.hideActions()
        }
        // The caption card itself swallows its taps so clicks on it never select
        // the component behind it.
        caption.view.isClickable = true
        caption.view.setOnClickListener { }

        instructionText = TextView(activity).apply {
            text = "Select a component"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#E6FFFFFF"))
            setTypeface(poppins(bold = true), Typeface.BOLD)
            isClickable = true
            setOnClickListener { }
        }

        centerHost = FrameLayout(activity).apply {
            addView(caption.view, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                leftMargin = dp(24)
                rightMargin = dp(64) // keep clear of the middle-right X
            })
            if (!tutorial) {
                addView(instructionText, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.CENTER })
            } else {
                caption.view.visibility = View.INVISIBLE
            }
        }
        root.addView(centerHost, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })

        if (!tutorial) {
            addCloseButton()
        }

        // Keep everything clear of the system bars (Help only shows the X + caption,
        // so the caption area needs no extra inset handling; the spotlight/scrim
        // already fills the window).
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        contentRoot.addView(root, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Help mode starts in its "select a component" state: light dim, subtle
        // instruction centered, nothing highlighted. Tutorial starts via goToStep(0).
        if (!tutorial) beginHelp()

        // Whenever the underlying layout changes size/position (insets, rotation,
        // first frame), re-snap the spotlight to the selected component so the
        // highlight always matches its real bounds.
        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            if (hasSelection()) refreshHighlight()
        }
        root.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener!!)
    }

    // =====================================================================
    //  HELP MODE (point-and-click)
    // =====================================================================

    /** Initial Help state: light dim, "Select a component", nothing highlighted. */
    fun beginHelp() {
        if (tutorial) return
        spotlight.setDimColor(lightDim)
        spotlight.setTargetRect(null)
        instructionText.visibility = View.VISIBLE
        caption.view.visibility = View.GONE
        currentIndex = -1
        captionY = 0f
        caption.view.translationY = 0f
    }

    // =====================================================================
    //  TUTORIAL MODE (sequential)
    // =====================================================================

    fun goToStep(index: Int) {
        if (!tutorial) return
        val clamped = index.coerceIn(0, targets.lastIndex)
        currentIndex = clamped

        btnBack.visibility = if (clamped == 0) View.GONE else View.VISIBLE
        btnNext.text = if (clamped == targets.lastIndex) "Got It!" else "Next"

        caption.setEyebrow("STEP ${clamped + 1} OF ${targets.size}")
        caption.setTitle(targets[clamped].title)
        caption.setDescription(targets[clamped].description)
        if (caption.view.visibility != View.VISIBLE) caption.view.visibility = View.VISIBLE

        spotlight.setDimColor(darkDim)
        targetView(clamped).post {
            highlightTarget(clamped)
            animateCaptionForStep(clamped)
        }
    }

    /** Smoothly places the caption for the current step: centered for steps 1-4,
     *  raised slightly for the conversation step (5), back to center on the last step. */
    private fun animateCaptionForStep(index: Int) {
        val toY = if (index == targets.lastIndex - 1) {
            // Step 5: lift the caption so the bottom conversation area is visible.
            -caption.view.height.toFloat() - dp(24)
        } else {
            // Steps 1-4 and the final step: normal centered position.
            0f
        }
        if (Math.abs(captionY - toY) < 1f) return
        ObjectAnimator
            .ofFloat(caption.view, "translationY", captionY, toY)
            .apply {
                duration = 460L
                interpolator = AccelerateDecelerateInterpolator() // ease-in/out, no bounce
            }
            .start()
        captionY = toY
    }

    fun nextStep() {
        if (currentIndex < targets.lastIndex) goToStep(currentIndex + 1) else finish()
    }

    fun backStep() {
        if (currentIndex > 0) goToStep(currentIndex - 1)
    }

    // =====================================================================
    //  SHARED SELECTION / HIGHLIGHT
    // =====================================================================

    private fun handleTouch(view: View, event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return
        if (tutorial) return
        if (currentIndex >= 0) return
        hitTest(event.x, event.y)?.let { selectTarget(it) }
    }

    /** Picks the SMALLEST component that contains the touch point — the most
     *  specific one wins (e.g. Reset beats the big camera area behind it). */
    private fun hitTest(x: Float, y: Float): Int? {
        var best: Int? = null
        var bestArea = Float.MAX_VALUE
        targets.forEachIndexed { index, target ->
            val rect = localRectOf(target.view) ?: return@forEachIndexed
            val hitRect = RectF(
                rect.left - dp(10), rect.top - dp(10),
                rect.right + dp(10), rect.bottom + dp(10)
            )
            if (hitRect.contains(x, y) && rect.width() * rect.height() < bestArea) {
                bestArea = rect.width() * rect.height()
                best = index
            }
        }
        return best
    }

    /** Selects a component for Help: dim dark, spotlight its REAL bounds, and swap
     *  the stable caption to its description. */
    private fun selectTarget(index: Int) {
        currentIndex = index
        val target = targets[index]
        instructionText.visibility = View.GONE

        caption.setTitle(target.title)
        caption.setDescription(target.description)
        caption.view.visibility = View.VISIBLE
        caption.view.alpha = 0f
        caption.view.animate()
            .alpha(1f)
            .setDuration(200L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        spotlight.setDimColor(darkDim)
        target.view.post { highlightTarget(index) }
    }

    private fun highlightTarget(index: Int) {
        val rect = localRectOf(targetView(index))
        spotlight.setTargetRect(rect?.let { RectF(it.left - dp(6), it.top - dp(6), it.right + dp(6), it.bottom + dp(6)) })
    }

    /** Recomputes the highlight from the target's CURRENT on-screen bounds. */
    private fun refreshHighlight() {
        if (currentIndex >= 0) targetView(currentIndex).post { highlightTarget(currentIndex) }
    }

    private fun hasSelection(): Boolean = currentIndex >= 0

    private fun targetView(index: Int): View = targets[index].view

    /** Maps [view]'s real on-screen bounds into the overlay's local coordinates. */
    private fun localRectOf(view: View): RectF? {
        val target = Rect()
        if (!view.getGlobalVisibleRect(target)) return null
        val overlay = Rect()
        root.getGlobalVisibleRect(overlay)
        return RectF(
            (target.left - overlay.left).toFloat(),
            (target.top - overlay.top).toFloat(),
            (target.right - overlay.left).toFloat(),
            (target.bottom - overlay.top).toFloat()
        )
    }

    // =====================================================================
    //  CONTROLS
    // =====================================================================

    private fun buildActions() {
        val btnSkip = actionGhost("Skip", gravityStart = true) { finish() }
        btnBack = actionGhost("Back", gravityStart = false) { backStep() }
        btnNext = actionPill()
        btnBack.visibility = View.GONE
        btnNext.text = "Next"

        caption.actionsRow.addView(btnSkip, actionGhostLayout(grow = true))
        caption.actionsRow.addView(btnBack, actionGhostLayout())
        caption.actionsRow.addView(btnNext, actionPillLayout())
    }

    /** Text-style action link with a consistent height + minimum width. */
    private fun actionGhost(text: String, gravityStart: Boolean, onClick: () -> Unit): TextView {
        return TextView(activity).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.parseColor("#8FA6BE"))
            typeface = poppins(bold = true)
            gravity = if (gravityStart) Gravity.CENTER_VERTICAL else Gravity.CENTER
            setPadding(dp(6), 0, dp(6), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    /** Consistent 3D blue pill for Next / Got It!. */
    private fun actionPill(): TextView {
        return TextView(activity).apply {
            text = "Next"
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = poppins(bold = true)
            gravity = Gravity.CENTER
            background = activity.getDrawable(R.drawable.bg_btn_blue_3d)
            setPadding(dp(22), 0, dp(22), dp(4))
            isClickable = true
            isFocusable = true
            minimumWidth = dp(120)
            setOnClickListener { nextStep() }
        }
    }

    private fun actionGhostLayout(grow: Boolean = false): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            if (grow) 0 else ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (grow) 1f else 0f
        )
    }

    private fun actionPillLayout(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    /** Circular 3D blue X — MIDDLE-RIGHT, always functional (a real child of the
     *  overlay, so it receives its own touches while everything else is blocked). */
    private fun addCloseButton() {
        root.addView(ImageButton(activity).apply {
            setImageResource(R.drawable.ic_close)
            background = activity.getDrawable(R.drawable.bg_circle_blue_3d)
            // Extra bottom padding pushes the X 2-3dp up for optical centering on
            // the raised 3D surface.
            setPadding(dp(12), dp(10), dp(12), dp(15))
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            contentDescription = "Close help"
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        }, FrameLayout.LayoutParams(dp(52), dp(52)).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            rightMargin = dp(10)
        })
    }

    private fun finish() {
        globalLayoutListener?.let { root.viewTreeObserver.removeOnGlobalLayoutListener(it) }
        globalLayoutListener = null
        (root.parent as? ViewGroup)?.removeView(root)
        onExit()
    }

    private fun poppins(bold: Boolean): Typeface {
        val font = if (bold) activity.resources.getFont(R.font.poppins_bold) else activity.resources.getFont(R.font.poppins_regular)
        return Typeface.create(font, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}