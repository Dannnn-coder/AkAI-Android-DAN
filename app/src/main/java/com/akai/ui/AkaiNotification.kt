package com.akai.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.akai.R

/**
 * Reusable AkAI notification — a themed, self-dismissing text box that replaces
 * Android Toast everywhere. It floats over the current screen, respects system
 * insets, wraps long messages, uses Poppins, and follows the active Light/Dark
 * theme. A single slot is reused (like Toast), so rapid notifications replace
 * each other instead of stacking.
 *
 * Optional [AkaiNotification.Action]s add small bold buttons (used by the
 * first-time instructions); tapping the action also dismisses the box.
 */
object AkaiNotification {

    const val DURATION_SHORT = 2200L
    const val DURATION_LONG  = 4200L

    data class Action(val label: String, val onClick: () -> Unit)

    private val handler = Handler(Looper.getMainLooper())
    private var activeHost: ViewGroup? = null
    private var activeView: View? = null
    private var hideRunnable: Runnable? = null

    /** Shorthand for a short, single-line notification (no title, no actions). */
    fun short(activity: Activity, message: String) {
        show(activity, message, durationMs = DURATION_SHORT)
    }

    /** Shorthand for a longer notification (no title, no actions). */
    fun long(activity: Activity, message: String) {
        show(activity, message, durationMs = DURATION_LONG)
    }

    fun show(
        activity: Activity,
        message: String,
        title: String? = null,
        durationMs: Long = DURATION_SHORT,
        actions: List<Action> = emptyList()
    ) {
        val host = activity.window.decorView as ViewGroup
        val dark = isDarkMode(activity)
        val density = activity.resources.displayMetrics.density

        hideNow()

        val card = buildCard(activity, dark, density, title, message, actions)

        // Appear just under the status bar and centered within the screen width. The
        // inset listener re-positions the card if insets resolve later than show().
        val baseTop = (16f * density).toInt()
        val statusInset = ViewCompat.getRootWindowInsets(host)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
            ?.top ?: 0
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = statusInset + baseTop
            leftMargin = (18f * density).toInt()
            rightMargin = (18f * density).toInt()
        }
        ViewCompat.setOnApplyWindowInsetsListener(card) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (view.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = bars.top + baseTop
            insets
        }
        host.addView(card, lp)

        card.alpha = 0f
        card.animate()
            .alpha(1f)
            .setDuration(180L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        activeHost = host
        activeView = card
        hideRunnable = Runnable { dismiss(host, card) }
        handler.postDelayed(hideRunnable!!, durationMs)
    }

    private fun buildCard(
        activity: Activity,
        dark: Boolean,
        density: Float,
        title: String?,
        message: String,
        actions: List<Action>
    ): View {
        val surface = if (dark) Color.parseColor("#1E1E1E") else Color.WHITE
        val depth   = if (dark) Color.parseColor("#000000") else Color.parseColor("#C9D6F0")
        val bodyText = if (dark) Color.parseColor("#E0E0E0") else Color.parseColor("#1F1F1F")
        val titleText = if (dark) Color.WHITE else Color.parseColor("#2196F3")
        val actionText = if (dark) Color.parseColor("#8AB4F8") else Color.parseColor("#2196F3")

        val card = FrameLayout(activity).apply {
            background = themedCardBackground(surface, depth, density)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (18f * density).toInt(),
                (14f * density).toInt(),
                (18f * density).toInt(),
                (14f * density).toInt()
            )
        }
        card.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        if (!title.isNullOrBlank()) {
            column.addView(TextView(activity).apply {
                text = title
                textSize = 15f
                typeface = poppins(activity, bold = true)
                setTextColor(titleText)
                setLineSpacing(0f, 1.05f)
            })
        }

        column.addView(TextView(activity).apply {
            text = message
            textSize = 13f
            typeface = poppins(activity, bold = false)
            setTextColor(bodyText)
            setLineSpacing(0f, 1.15f)
        })

        if (actions.isNotEmpty()) {
            val actionRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, (6f * density).toInt(), 0, 0)
            }
            actions.forEach { action ->
                actionRow.addView(TextView(activity).apply {
                    text = action.label
                    textSize = 14f
                    typeface = poppins(activity, bold = true)
                    setTextColor(actionText)
                    gravity = Gravity.CENTER
                    setPadding((14f * density).toInt(), (8f * density).toInt(), (10f * density).toInt(), (4f * density).toInt())
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        dismiss(activity.window.decorView as ViewGroup, card)
                        action.onClick()
                    }
                })
            }
            column.addView(actionRow)
        }

        // Desired width: 420dp max, never wider than the screen minus margins.
        val screenWidth = activity.resources.displayMetrics.widthPixels
        val maxWidth = screenWidth - (36f * density).toInt()
        val targetWidth = minOf((420f * density).toInt(), maxWidth)
        card.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(targetWidth, android.view.View.MeasureSpec.AT_MOST),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        )
        card.layoutParams = FrameLayout.LayoutParams(
            minOf(targetWidth, card.measuredWidth),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return card
    }

    /** AkAI 3D-style card: surface layer floating over a darker depth layer. */
    private fun themedCardBackground(surface: Int, depth: Int, density: Float): LayerDrawable {
        val radius = (14f * density)
        val depthLayer = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(depth)
            cornerRadius = radius
        }
        val surfaceLayer = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(surface)
            cornerRadius = radius
        }
        val inset = (2.5f * density).toInt()
        val layers = LayerDrawable(arrayOf(depthLayer, surfaceLayer))
        layers.setLayerInset(0, 0, inset, 0, 0)
        layers.setLayerInset(1, 0, 0, 0, inset)
        return layers
    }

    private fun poppins(context: Context, bold: Boolean): Typeface {
        val font = if (bold) context.resources.getFont(R.font.poppins_bold) else context.resources.getFont(R.font.poppins_regular)
        return Typeface.create(font, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun isDarkMode(activity: Activity): Boolean {
        val mode = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun dismiss(host: ViewGroup, card: View) {
        hideRunnable?.let { handler.removeCallbacks(it) }
        hideRunnable = null
        if (activeView !== card) return
        card.animate()
            .alpha(0f)
            .setDuration(200L)
            .setInterpolator(android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f))
            .withEndAction {
                if (card.parent === host) host.removeView(card)
                if (activeView === card) {
                    activeView = null
                    activeHost = null
                }
            }
            .start()
    }

    /** Immediately removes whatever is showing (called before a new show). */
    private fun hideNow() {
        hideRunnable?.let { handler.removeCallbacks(it) }
        hideRunnable = null
        val host = activeHost
        val view = activeView
        if (host != null && view != null && view.parent === host) {
            host.removeView(view)
        }
        activeHost = null
        activeView = null
    }
}