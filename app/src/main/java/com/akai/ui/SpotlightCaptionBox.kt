package com.akai.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The caption card shown over a [SpotlightView] overlay, describing the current target.
 * Flips between the top and bottom edge of the screen to avoid covering the spotlighted target.
 */
class SpotlightCaptionBox(private val activity: Activity) {

    val view: LinearLayout
    val actionsRow: LinearLayout
    private val tvEyebrow: TextView
    private val tvTitle: TextView
    private val tvDescription: TextView

    init {
        tvEyebrow = TextView(activity).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#9090A8"))
            letterSpacing = 0.08f
        }
        tvTitle = TextView(activity).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(6))
        }
        tvDescription = TextView(activity).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#BDBDBD"))
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        actionsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        view = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E1E1E"))
                cornerRadius = dp(12).toFloat()
            }
            addView(tvEyebrow)
            addView(tvTitle)
            addView(tvDescription)
            addView(actionsRow)
        }
    }

    fun setEyebrow(text: String) { tvEyebrow.text = text }
    fun setTitle(text: String) { tvTitle.text = text }
    fun setDescription(text: String) { tvDescription.text = text }

    /**
     * Repositions [view] to the top or bottom edge of the screen, whichever [targetRect]
     * doesn't occupy. [bottomReserve] adds extra bottom clearance (e.g. for a floating exit button).
     */
    fun avoidOverlap(targetRect: RectF?, bottomReserve: Int = 0) {
        val screenWidth = activity.resources.displayMetrics.widthPixels
        val screenHeight = activity.resources.displayMetrics.heightPixels
        view.measure(
            View.MeasureSpec.makeMeasureSpec(screenWidth - dp(40), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val captionHeight = view.measuredHeight
        val edgeMargin = dp(28)
        val bottomCaptionTop = screenHeight - edgeMargin - bottomReserve - captionHeight
        val overlapsBottom = targetRect != null && targetRect.bottom > bottomCaptionTop

        val params = view.layoutParams as FrameLayout.LayoutParams
        if (overlapsBottom) {
            params.gravity = Gravity.TOP
            params.topMargin = edgeMargin
            params.bottomMargin = 0
        } else {
            params.gravity = Gravity.BOTTOM
            params.bottomMargin = edgeMargin + bottomReserve
            params.topMargin = 0
        }
        view.layoutParams = params
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
