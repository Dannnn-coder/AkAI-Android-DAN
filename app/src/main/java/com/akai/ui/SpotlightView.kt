package com.akai.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.view.View

/** Draws a dimmed scrim with a soft, feathered-edge cutout around [targetRect]. */
class SpotlightView(context: Context) : View(context) {
    private var targetRect: RectF? = null
    private val dimPaint = Paint().apply { color = Color.parseColor("#CC000000") }
    private val featherRadius = 18f * context.resources.displayMetrics.density
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        maskFilter = BlurMaskFilter(featherRadius, BlurMaskFilter.Blur.NORMAL)
    }
    private val cornerRadius = 12f * context.resources.displayMetrics.density
    private var offscreenBitmap: Bitmap? = null
    private var offscreenCanvas: Canvas? = null

    init {
        // CLEAR blending + BlurMaskFilter both require an unaccelerated software layer
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setTargetRect(rect: RectF?) {
        targetRect = rect
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            offscreenBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            offscreenCanvas = Canvas(offscreenBitmap!!)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val bitmap = offscreenBitmap ?: return
        val offCanvas = offscreenCanvas ?: return
        bitmap.eraseColor(Color.TRANSPARENT)
        offCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        targetRect?.let { rect ->
            offCanvas.drawRoundRect(rect, cornerRadius, cornerRadius, clearPaint)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, null)
    }
}

/** Maps [target]'s on-screen bounds into [overlayRoot]'s local coordinate space, with padding. */
fun computeSpotlightRect(target: View, overlayRoot: View, paddingPx: Int): RectF {
    val targetBounds = Rect()
    target.getGlobalVisibleRect(targetBounds)
    val overlayBounds = Rect()
    overlayRoot.getGlobalVisibleRect(overlayBounds)
    targetBounds.offset(-overlayBounds.left, -overlayBounds.top)
    return RectF(
        (targetBounds.left - paddingPx).toFloat(),
        (targetBounds.top - paddingPx).toFloat(),
        (targetBounds.right + paddingPx).toFloat(),
        (targetBounds.bottom + paddingPx).toFloat()
    )
}
