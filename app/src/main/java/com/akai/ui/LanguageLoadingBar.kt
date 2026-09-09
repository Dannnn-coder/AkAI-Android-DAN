package com.akai.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

/**
 * Slim white loading bar for the in-container language-model switch.
 *
 * Not a system ProgressBar. While a model loads, a soft white "packet" flows
 * back and forth along a subtle track (rounded ends, gentle ease in-out each
 * way) — fluid and calm, matching the blue voice area.[start] begins the flow,
 * [stop] freezes and clears it.
 */
class LanguageLoadingBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 48
    }
    private val packetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 225
    }
    private val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    private var travel = 0f

    private var animator: ValueAnimator? = null

    /** Begins the flowing movement (full hold at both ends, eased in between). */
    fun start() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500L
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                // Sine drives a smooth out-and-back that eases at the extremes.
                val raw = it.animatedValue as Float
                travel = (sin(Math.PI * raw)).toFloat()
                invalidate()
            }
            start()
        }
    }

    /** Stops the flow and returns the bar to an empty track. */
    fun stop() {
        animator?.cancel()
        animator = null
        travel = 0f
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(Math.round(dp(16f)))
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val centerY = height / 2f
        val barHalf = dp(3f)

        val pad = dp(8f)
        val trackLeft = pad
        val trackRight = w - pad
        if (trackRight <= trackLeft) return

        // Track: slim, translucent, fully rounded ends.
        canvas.drawRoundRect(trackLeft, centerY - barHalf, trackRight, centerY + barHalf, barHalf, barHalf, trackPaint)
        if (animator == null) return

        // Flowing packet + a crisp leading tip that gently outruns the packet.
        val x = trackLeft + (trackRight - trackLeft) * travel
        val packetHalf = dp(28f)
        canvas.drawRoundRect(
            x - packetHalf, centerY - barHalf - dp(1f),
            x + packetHalf, centerY + barHalf + dp(1f),
            barHalf, barHalf, packetPaint
        )
        canvas.drawCircle(x + packetHalf * 0.7f, centerY, dp(4f), tipPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        animator = null
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}