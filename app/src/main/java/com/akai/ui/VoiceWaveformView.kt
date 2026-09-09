package com.akai.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Voice waveform strip for the Voice mode "Tap to speak" area.
 *
 * The bars are NOT an animation. Height is driven exclusively by the real
 * microphone amplitude published by [setLevel] (0..1) while recording:
 *
 *   - NOT RECORDING : a simple static horizontal line.
 *   - RECORDING     : fixed waveform silhouette scaled up/down by the actual
 *                     mic level. Silence stays near-flat; speech moves it.
 */
class VoiceWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(6f)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = dp(3f)
        alpha = 110
    }

    private val barCount = 19

    // Static silhouette (center-tallest envelope with a gentle ripple). It only
    // MULTIPLIES the live mic level — it never animates by itself.
    private val profile = FloatArray(barCount) { i ->
        val n = i - (barCount - 1) / 2f
        val envelope = exp(-(n * n) / 42f)
        val ripple = abs(sin(i * 1.15f + 0.6f))
        (0.35f + 0.65f * ripple) * envelope
    }

    private var active = false
    private var level = 0f

    /** Toggles idle line (false) vs live-bars (true). Resets the level when idle. */
    fun setActive(isActive: Boolean) {
        if (isActive == active) return
        active = isActive
        if (!active) level = 0f
        invalidate()
    }

    /** Real microphone amplitude, 0..1. Only meaningful while [active]. */
    fun setLevel(amplitude: Float) {
        level = amplitude.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        active = false
        level = 0f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerY = height / 2f

        if (!active) {
            canvas.drawLine(0f, centerY, width.toFloat(), centerY, linePaint)
            return
        }

        // Peak bar height comes from the live mic level only; a tiny minimum
        // keeps silence reading as a near-flat line instead of fully vanishing.
        // 0.72 leaves headroom so even peak bars stay inside the view.
        val maxBar = height * 0.72f
        val amp = dp(3f) + maxBar * level
        val step = width / barCount.toFloat()

        for (i in 0 until barCount) {
            val barAmp = (amp * profile[i]).coerceAtLeast(dp(3f))
            val x = step * (i + 0.5f)
            canvas.drawLine(x, centerY - barAmp, x, centerY + barAmp, barPaint)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}