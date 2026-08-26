package com.akai.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

/**
 * On-demand "Help Mode": dims the screen and lets the user tap any real component
 * to see what it does. Exits via the round X button on the right edge of the screen.
 *
 * Component bounds are recomputed at the moment of each tap (not cached), since the
 * live camera feed keeps updating the layout underneath while this overlay is open.
 */
class HelpModeOverlay(
    private val activity: Activity,
    private val components: List<CoachMarkTutorial.Step>,
    private val onExit: () -> Unit
) {
    private lateinit var overlayRoot: FrameLayout
    private lateinit var spotlightView: SpotlightView
    private lateinit var caption: SpotlightCaptionBox

    fun start() {
        val contentRoot = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)

        spotlightView = SpotlightView(activity).apply {
            isClickable = true
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) handleTap(event.x, event.y)
                true // always consume — nothing underneath should be reachable while this is open
            }
        }
        caption = SpotlightCaptionBox(activity).apply {
            setEyebrow("HELP")
            setTitle("What do you want to know about?")
            setDescription("Tap anything on the screen and I'll explain it here.")
        }

        overlayRoot = FrameLayout(activity)
        overlayRoot.addView(spotlightView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        overlayRoot.addView(caption.view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
            leftMargin = dp(20)
            rightMargin = dp(20)
        })
        overlayRoot.addView(buildExitButton(), FrameLayout.LayoutParams(dp(52), dp(52)).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            rightMargin = dp(16)
        })

        contentRoot.addView(overlayRoot, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        caption.avoidOverlap(null)
    }

    /** Finds which component (if any) currently occupies (x, y) and selects the most specific match. */
    private fun handleTap(x: Float, y: Float) {
        val hit = components
            .map { it to computeSpotlightRect(it.target, overlayRoot, dp(8)) }
            .filter { (_, rect) -> rect.contains(x, y) }
            .minByOrNull { (_, rect) -> rect.width() * rect.height() } // smallest = most specific
            ?: return
        select(hit.first, hit.second)
    }

    private fun select(component: CoachMarkTutorial.Step, rect: RectF) {
        spotlightView.setTargetRect(rect)
        caption.setEyebrow("ABOUT THIS")
        caption.setTitle(component.title)
        caption.setDescription(component.description)
        caption.avoidOverlap(rect)
    }

    private fun buildExitButton(): TextView {
        return TextView(activity).apply {
            text = "✕"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#263238"))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        }
    }

    private fun finish() {
        (overlayRoot.parent as? ViewGroup)?.removeView(overlayRoot)
        onExit()
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
