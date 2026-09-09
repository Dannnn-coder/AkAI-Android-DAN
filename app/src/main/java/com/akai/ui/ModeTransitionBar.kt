package com.akai.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator

/**
 * Custom stylized progress bar for the full-screen mode transition.
 *
 * Not a system ProgressBar. It draws a thin track + a rounded white fill capped
 * by a softly pulsing head dot. While the target mode is initializing it flows
 * back and forth (smooth, never a 0->100 jump); [finish] glides the fill to 100%
 * once the mode is truly ready so the panel can exit.
 */
class ModeTransitionBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barHeight = dp(5f)
    private val headRadius = dp(5.5f)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 70
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    private var progress = 0f
    private var glow = 0f

    private var floatAnimator: ValueAnimator? = null
    private var glowAnimator: ValueAnimator? = null

    /** Begins the indeterminate flowing movement (swells, eases back — pulsing). */
    fun startPulsing() {
        stopAnimators()
        floatAnimator = ValueAnimator.ofFloat(0.12f, 0.82f).apply {
            duration = 1300L
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
        glowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { glow = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    /** Glides the fill to 100% and invokes [onDone] when it lands. Idempotent. */
    fun finish(onDone: () -> Unit) {
        stopAnimators()
        if (progress >= 0.99f) {
            onDone()
            return
        }
        ValueAnimator.ofFloat(progress, 1f).apply {
            duration = 250L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                glow = 1f
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onDone()
            })
            start()
        }
    }

    fun reset() {
        stopAnimators()
        progress = 0f
        glow = 0f
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(Math.round(dp(18f)))
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val centerY = height / 2f
        val half = barHeight / 2f
        val trackLeft = headRadius
        val trackRight = w - headRadius
        if (trackRight <= trackLeft) return

        // Track: subtle translucent white, fully rounded at both ends.
        canvas.drawRoundRect(trackLeft, centerY - half, trackRight, centerY + half, half, half, trackPaint)

        // Fill: crisp white, grows as the mode initializes.
        val fillEnd = trackLeft + (trackRight - trackLeft) * progress
        if (fillEnd > trackLeft) {
            canvas.drawRoundRect(trackLeft, centerY - half, fillEnd, centerY + half, half, half, fillPaint)
        }

        // Pulsing head: a soft glow that breathes as it travels with the fill.
        val tip = fillEnd.coerceAtLeast(trackLeft)
        headPaint.alpha = 150 + (105 * glow).toInt()
        canvas.drawCircle(tip, centerY, headRadius + dp(1.5f) * glow, headPaint)
        headPaint.alpha = 255
        canvas.drawCircle(tip, centerY, dp(3.5f), headPaint)
    }

    private fun stopAnimators() {
        floatAnimator?.cancel()
        glowAnimator?.cancel()
        floatAnimator = null
        glowAnimator = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimators()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}