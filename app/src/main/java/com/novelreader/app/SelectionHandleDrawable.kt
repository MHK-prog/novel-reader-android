package com.novelreader.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.roundToInt

/** Teardrop text-selection handle drawn at the native editor's requested bounds. */
class SelectionHandleDrawable(
    context: Context,
    color: Int,
    private val pointDirection: Int,
) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 48f, bounds.height() / 24f)
        val offsetX = when {
            pointDirection > 0 -> 24f
            pointDirection < 0 -> 0f
            else -> 12f
        }
        canvas.translate(offsetX, -2f)
        val shape = Path().apply {
            moveTo(12f, 22f)
            cubicTo(16.4183f, 22f, 20f, 18.4183f, 20f, 14f)
            cubicTo(20f, 9.58172f, 12f, 2f, 12f, 2f)
            cubicTo(12f, 2f, 4f, 9.58172f, 4f, 14f)
            cubicTo(4f, 18.4183f, 7.58172f, 22f, 12f, 22f)
            close()
        }
        canvas.drawPath(shape, paint)
        canvas.restore()
    }

    override fun getIntrinsicWidth(): Int = dp(48f).roundToInt()
    override fun getIntrinsicHeight(): Int = dp(24f).roundToInt()
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun dp(value: Float): Float = value * density
}
