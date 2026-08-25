package com.akai.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A spotlight-style walkthrough: dims the whole screen except a cutout around
 * the current target view, with a caption box explaining it that avoids covering it.
 */
class CoachMarkTutorial(
    private val activity: Activity,
    private val steps: List<Step>,
    private val onFinished: () -> Unit
) {
    data class Step(val target: View, val title: String, val description: String)

    private var currentIndex = 0
    private lateinit var overlayRoot: FrameLayout
    private lateinit var spotlightView: SpotlightView
    private lateinit var caption: SpotlightCaptionBox
    private lateinit var btnNext: TextView

    fun start() {
        if (steps.isEmpty()) {
            onFinished()
            return
        }
        buildOverlay()
        showStep(0)
    }

    private fun buildOverlay() {
        val contentRoot = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)

        spotlightView = SpotlightView(activity).apply {
            setOnClickListener { advance() }
        }

        caption = SpotlightCaptionBox(activity)
        buildActions()

        overlayRoot = FrameLayout(activity).apply {
            addView(spotlightView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(caption.view, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                leftMargin = dp(20)
                rightMargin = dp(20)
                bottomMargin = dp(28)
            })
        }

        contentRoot.addView(overlayRoot, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    private fun buildActions() {
        val btnSkip = TextView(activity).apply {
            text = "Skip"
            textSize = 14f
            setTextColor(Color.parseColor("#9090A8"))
            setPadding(dp(4), dp(8), dp(4), dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        btnNext = TextView(activity).apply {
            text = "Next"
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A237E"))
                cornerRadius = dp(6).toFloat()
            }
            setPadding(dp(22), dp(9), dp(22), dp(9))
            isClickable = true
            isFocusable = true
            setOnClickListener { advance() }
        }
        caption.actionsRow.addView(btnSkip)
        caption.actionsRow.addView(btnNext)
    }

    private fun showStep(index: Int) {
        currentIndex = index
        val step = steps[index]
        caption.setEyebrow("STEP ${index + 1} OF ${steps.size}")
        caption.setTitle(step.title)
        caption.setDescription(step.description)
        btnNext.text = if (index == steps.lastIndex) "Got it" else "Next"

        step.target.post {
            val rect = computeSpotlightRect(step.target, overlayRoot, dp(8))
            spotlightView.setTargetRect(rect)
            caption.avoidOverlap(rect)
        }
    }

    private fun advance() {
        if (currentIndex < steps.lastIndex) showStep(currentIndex + 1) else finish()
    }

    private fun finish() {
        (overlayRoot.parent as? ViewGroup)?.removeView(overlayRoot)
        onFinished()
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
