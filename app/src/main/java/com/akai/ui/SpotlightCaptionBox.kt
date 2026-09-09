package com.akai.ui

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The stable custom AkAI caption card shown over the overlay. It has a FIXED
 * height for its sub-regions (eyebrow / title / description / actions row), so
 * changing the description text can NEVER resize the card or move the action
 * buttons — this is what keeps the first-time tutorial from "jumping" between
 * steps. Follows the active Light/Dark theme (3D AkAI card, Poppins).
 */
class SpotlightCaptionBox(private val activity: Activity) {

    val view: LinearLayout
    val actionsRow: LinearLayout
    private val tvEyebrow: TextView
    private val tvTitle: TextView
    private val tvDescription: TextView

    private val dark: Boolean
        get() {
            val mode = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return mode == Configuration.UI_MODE_NIGHT_YES
        }

    init {
        val density = activity.resources.displayMetrics.density
        tvEyebrow = TextView(activity).apply {
            textSize = 11f
            setTextColor(if (dark) Color.parseColor("#9090A8") else Color.parseColor("#6A6A80"))
            letterSpacing = 0.08f
            typeface = poppins(bold = true)
            visibility = View.GONE
        }
        tvTitle = TextView(activity).apply {
            textSize = 16f
            setTextColor(if (dark) Color.WHITE else Color.parseColor("#2196F3"))
            setTypeface(poppins(bold = true), Typeface.BOLD)
            maxLines = 1
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
        }
        tvDescription = TextView(activity).apply {
            textSize = 13f
            setTextColor(if (dark) Color.parseColor("#E0E0E0") else Color.parseColor("#1F1F1F"))
            setLineSpacing(0f, 1.18f)
            typeface = poppins(bold = false)
            maxLines = 4
            gravity = Gravity.TOP
        }
        actionsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        view = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(12))
            background = cardBackground()

            // FIXED sub-region heights — the card's total height never depends on the
            // current description text, so the tutorial stays perfectly still.
            addView(tvEyebrow, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(18)
            ))
            addView(tvTitle, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(28)
            ))
            addView(tvDescription, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(72)
            ))
            addView(actionsRow, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)
            ))
        }
    }

    fun setEyebrow(text: String) {
        tvEyebrow.text = text
        tvEyebrow.visibility = View.VISIBLE
    }

    fun hideEyebrow() {
        tvEyebrow.text = ""
        tvEyebrow.visibility = View.GONE
    }

    fun setTitle(text: String) { tvTitle.text = text }
    fun setDescription(text: String) { tvDescription.text = text }

    fun showActions() { actionsRow.visibility = View.VISIBLE }
    fun hideActions() { actionsRow.visibility = View.GONE }

    /**
     * Positions the caption with an explicit [Gravity] and margins (used by the
     * shared overlay to keep it centered and stable).
     */
    fun place(gravity: Int, topMargin: Int = 0, bottomMargin: Int = 0) {
        view.translationY = 0f
        val params = view.layoutParams as FrameLayout.LayoutParams
        params.gravity = gravity
        params.topMargin = topMargin
        params.bottomMargin = bottomMargin
        view.layoutParams = params
    }

    /** Flips to the screen edge [targetRect] doesn't occupy (kept for compatibility; the
     *  overlay itself normally keeps the caption centered for stability.) */
    fun avoidOverlap(targetRect: RectF?) {
        val screenWidth = activity.resources.displayMetrics.widthPixels
        val screenHeight = activity.resources.displayMetrics.heightPixels
        view.measure(
            View.MeasureSpec.makeMeasureSpec(screenWidth - dp(40), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val captionHeight = view.measuredHeight
        val edgeMargin = dp(28)
        val bottomCaptionTop = screenHeight - edgeMargin - captionHeight
        val overlapsBottom = targetRect != null && targetRect.bottom > bottomCaptionTop
        place(
            if (overlapsBottom) Gravity.TOP else Gravity.BOTTOM,
            topMargin = if (overlapsBottom) edgeMargin else 0,
            bottomMargin = if (!overlapsBottom) edgeMargin else 0
        )
    }

    /** AkAI 3D-style caption card: surface over a darker depth layer, rounded. */
    private fun cardBackground(): LayerDrawable {
        val density = activity.resources.displayMetrics.density
        val radius = dp(14).toFloat()
        val depthLayer = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(if (dark) Color.parseColor("#000000") else Color.parseColor("#C9D6F0"))
            cornerRadius = radius
        }
        val surfaceLayer = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(if (dark) Color.parseColor("#1E1E1E") else Color.WHITE)
            cornerRadius = radius
        }
        val inset = (2.5f * density).toInt()
        val layers = LayerDrawable(arrayOf(depthLayer, surfaceLayer))
        layers.setLayerInset(0, 0, inset, 0, 0)
        layers.setLayerInset(1, 0, 0, 0, inset)
        return layers
    }

    private fun poppins(bold: Boolean): Typeface {
        val font = if (bold) {
            activity.resources.getFont(com.akai.R.font.poppins_bold)
        } else {
            activity.resources.getFont(com.akai.R.font.poppins_regular)
        }
        return Typeface.create(font, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}