package com.novelreader.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/** Lightweight native splash illustration, kept separate from the activity. */
class ReaderSplashArt(context: Context, private val accent: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pulse = 1f

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    fun setPulse(value: Float) {
        pulse = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        canvas.save()
        canvas.scale(pulse, pulse, cx, cy)

        val glowRadius = dp(50f)
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx, cy, glowRadius,
            intArrayOf(Color.argb(90, Color.red(accent), Color.green(accent), Color.blue(accent)), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, glowRadius, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.2f)
        paint.color = Color.argb(85, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawCircle(cx, cy, dp(42f), paint)
        val orbit = RectF(cx - dp(52f), cy - dp(25f), cx + dp(52f), cy + dp(25f))
        paint.strokeWidth = dp(1f)
        canvas.drawArc(orbit, 205f, 220f, false, paint)

        paint.style = Paint.Style.FILL
        paint.color = accent
        canvas.drawCircle(cx - dp(44f), cy - dp(12f), dp(2.2f), paint)
        canvas.drawCircle(cx + dp(47f), cy + dp(7f), dp(1.7f), paint)
        paint.color = 0xFFDCC6FF.toInt()
        canvas.drawCircle(cx + dp(27f), cy - dp(37f), dp(1.5f), paint)

        val leftPage = Path().apply {
            moveTo(cx, cy - dp(19f))
            cubicTo(cx - dp(11f), cy - dp(27f), cx - dp(24f), cy - dp(26f), cx - dp(31f), cy - dp(23f))
            lineTo(cx - dp(31f), cy + dp(19f))
            cubicTo(cx - dp(19f), cy + dp(14f), cx - dp(8f), cy + dp(17f), cx, cy + dp(25f))
            close()
        }
        val rightPage = Path().apply {
            moveTo(cx, cy - dp(19f))
            cubicTo(cx + dp(11f), cy - dp(27f), cx + dp(24f), cy - dp(26f), cx + dp(31f), cy - dp(23f))
            lineTo(cx + dp(31f), cy + dp(19f))
            cubicTo(cx + dp(19f), cy + dp(14f), cx + dp(8f), cy + dp(17f), cx, cy + dp(25f))
            close()
        }
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            cx - dp(31f), cy, cx + dp(31f), cy,
            intArrayOf(0xFF7B35EB.toInt(), accent, 0xFFD9BCFF.toInt()), null, Shader.TileMode.CLAMP,
        )
        canvas.drawPath(leftPage, paint)
        canvas.drawPath(rightPage, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.4f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = 0xFFEDE1FF.toInt()
        canvas.drawPath(leftPage, paint)
        canvas.drawPath(rightPage, paint)
        canvas.drawLine(cx, cy - dp(18f), cx, cy + dp(23f), paint)
        canvas.drawLine(cx - dp(24f), cy - dp(13f), cx - dp(10f), cy - dp(10f), paint)
        canvas.drawLine(cx - dp(23f), cy - dp(5f), cx - dp(10f), cy - dp(2f), paint)
        canvas.drawLine(cx + dp(10f), cy - dp(10f), cx + dp(24f), cy - dp(13f), paint)
        canvas.drawLine(cx + dp(10f), cy - dp(2f), cx + dp(23f), cy - dp(5f), paint)
        canvas.restore()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
