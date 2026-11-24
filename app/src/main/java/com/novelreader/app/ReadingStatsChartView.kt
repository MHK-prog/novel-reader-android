package com.novelreader.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import java.util.Locale
import java.util.TreeMap
import java.util.TreeSet
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ReadingStatsChartView(
    context: Context,
    accentColor: Int,
    mutedColor: Int,
    typeface: Typeface?,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var points: List<ReadingStatsStore.Point> = emptyList()
    private var detailsVisible = false
    private var showPercentages = false
    private var accentColor = accentColor
    private var mutedColor = mutedColor
    private var typeface = typeface
    private val bookColors = HashMap<String, Int>()

    fun setData(
        points: List<ReadingStatsStore.Point>?,
        accentColor: Int,
        mutedColor: Int,
        typeface: Typeface?,
        detailsVisible: Boolean,
        showPercentages: Boolean,
    ) {
        this.points = points?.toList() ?: emptyList()
        this.accentColor = accentColor
        this.mutedColor = mutedColor
        this.typeface = typeface
        this.detailsVisible = detailsVisible
        this.showPercentages = showPercentages
        bookColors.clear()
        val bookIds = TreeSet<String>()
        this.points.forEach { bookIds.addAll(it.byBook.keys) }
        bookIds.forEachIndexed { index, id ->
            bookColors[id] = BOOK_COLORS[index % BOOK_COLORS.size]
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) return
        val density = resources.displayMetrics.density
        val left = 46 * density
        val right = width - 9 * density
        val top = 18 * density
        val bottom = height - 34 * density
        if (right <= left || bottom <= top) return

        var maximum = 0L
        points.forEach { maximum = max(maximum, it.value) }
        if (maximum == 0L) maximum = 60_000

        paint.typeface = typeface ?: Typeface.DEFAULT
        paint.textSize = 10 * density
        paint.textAlign = Paint.Align.RIGHT
        paint.style = Paint.Style.FILL
        paint.color = mutedColor
        for (line in 0..4) {
            val ratio = line / 4f
            val y = bottom - (bottom - top) * ratio
            paint.color = 0x557F7887
            paint.strokeWidth = density
            canvas.drawLine(left, y, right, y, paint)
            paint.color = mutedColor
            canvas.drawText(formatValue(maximum * line / 4.0), left - 6 * density, y + 3 * density, paint)
        }

        val slotWidth = (right - left) / points.size
        val barWidth = max(2 * density, slotWidth * .58f)
        paint.style = Paint.Style.FILL
        points.forEachIndexed { index, point ->
            val center = left + slotWidth * (index + .5f)
            val totalHeight = (point.value / maximum.toDouble()).toFloat() * (bottom - top)
            if (!detailsVisible || point.byBook.isEmpty()) {
                paint.color = accentColor
                val bar = RectF(center - barWidth / 2, bottom - totalHeight, center + barWidth / 2, bottom)
                val radius = min(barWidth / 2, 5 * density)
                canvas.drawRoundRect(bar, radius, radius, paint)
                return@forEachIndexed
            }

            val segments = TreeMap(point.byBook)
            val visibleTotal = segments.values.sum()
            val remainder = max(0L, point.value - visibleTotal)
            var cursor = bottom
            for ((id, value) in segments) {
                val segmentHeight = (value / maximum.toDouble()).toFloat() * (bottom - top)
                if (segmentHeight <= 0) continue
                paint.color = colorForBook(id)
                canvas.drawRect(
                    RectF(center - barWidth / 2, cursor - segmentHeight, center + barWidth / 2, cursor),
                    paint,
                )
                if (showPercentages && point.value > 0 && segmentHeight >= 18 * density) {
                    paint.color = Color.WHITE
                    paint.textAlign = Paint.Align.CENTER
                    paint.textSize = 9 * density
                    val percent = (value * 100.0 / point.value).roundToInt()
                    canvas.drawText(percent.toString(), center, cursor - segmentHeight / 2 + 3 * density, paint)
                }
                cursor -= segmentHeight
            }
            if (remainder > 0) {
                val segmentHeight = (remainder / maximum.toDouble()).toFloat() * (bottom - top)
                paint.color = 0xFF77717D.toInt()
                canvas.drawRect(
                    RectF(center - barWidth / 2, cursor - segmentHeight, center + barWidth / 2, cursor),
                    paint,
                )
                if (showPercentages && point.value > 0 && segmentHeight >= 18 * density) {
                    paint.color = Color.WHITE
                    paint.textAlign = Paint.Align.CENTER
                    paint.textSize = 9 * density
                    val percent = (remainder * 100.0 / point.value).roundToInt()
                    canvas.drawText(percent.toString(), center, cursor - segmentHeight / 2 + 3 * density, paint)
                }
            }
        }

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 10 * density
        paint.color = mutedColor
        points.forEachIndexed { index, point ->
            if (point.label.isNotEmpty()) {
                canvas.drawText(point.label, left + slotWidth * (index + .5f), height - 10 * density, paint)
            }
        }

        if (maximum == 60_000L) {
            paint.textSize = 13 * density
            paint.color = mutedColor
            canvas.drawText("هنوز آماری ثبت نشده", (left + right) / 2, (top + bottom) / 2, paint)
        }
    }

    fun colorForBook(id: String): Int = bookColors[id]
        ?: BOOK_COLORS[(id.hashCode() and Int.MAX_VALUE) % BOOK_COLORS.size]

    private fun formatValue(raw: Double): String {
        val hours = raw / 3_600_000.0
        return if (hours >= 1) String.format(Locale.ROOT, "%.0f", hours)
        else String.format(Locale.ROOT, "%.0f", raw / 60_000.0)
    }

    companion object {
        private val BOOK_COLORS = intArrayOf(
            0xFF9B7BFF.toInt(), 0xFF43A7F5.toInt(), 0xFF4FC38B.toInt(), 0xFFFF6B6B.toInt(),
            0xFFFFB454.toInt(), 0xFF4DC9C0.toInt(), 0xFFED78B5.toInt(), 0xFF8ABF4F.toInt(),
            0xFF7788FF.toInt(), 0xFFCE8A55.toInt(),
        )
    }
}
