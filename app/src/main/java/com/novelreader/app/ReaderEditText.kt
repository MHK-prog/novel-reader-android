package com.novelreader.app

import android.content.Context
import android.text.Editable
import android.view.MotionEvent
import android.view.ViewParent
import android.widget.EditText
import kotlin.math.hypot

/** Edit field gestures used by the chapter reader. */
class ReaderEditText(context: Context) : EditText(context) {
    private var startX = 0f
    private var startY = 0f
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var lastTapTime = 0L
    private var tapCount = 0
    private var tapMoved = false

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun distance(x: Float, y: Float): Double = hypot(x.toDouble(), y.toDouble())

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                tapMoved = false
                allowParentScroll()
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = kotlin.math.abs(event.y - startY)
                if (deltaY > dp(8f) && deltaY > kotlin.math.abs(event.x - startX) * 1.2f) {
                    allowParentScroll()
                    if (distance(event.x - startX, event.y - startY) > dp(24f)) tapMoved = true
                }
            }
            MotionEvent.ACTION_UP -> {
                val now = event.eventTime
                val shortTap = !tapMoved && now - event.downTime < 500L &&
                    distance(event.x - startX, event.y - startY) < dp(24f)
                tapCount = if (shortTap && now - lastTapTime < 650L &&
                    distance(event.x - lastTapX, event.y - lastTapY) < dp(36f)
                ) tapCount + 1 else if (shortTap) 1 else 0
                lastTapTime = now
                lastTapX = event.x
                lastTapY = event.y
                if (tapCount >= 3) {
                    val offset = getOffsetForPosition(event.x, event.y)
                    tapCount = 0
                    post { selectLogicalLine(offset) }
                }
            }
        }
        return handled
    }

    private fun selectLogicalLine(offset: Int) {
        val editable: Editable = text ?: return
        if (editable.isEmpty()) return
        val value = editable.toString()
        val safeOffset = offset.coerceIn(0, value.length)
        val lineStart = if (safeOffset == 0) 0 else value.lastIndexOf('\n', safeOffset - 1) + 1
        var lineEnd = value.indexOf('\n', safeOffset)
        if (lineEnd < 0) lineEnd = value.length
        if (lineEnd == lineStart && lineEnd < value.length) lineEnd++
        setSelection(lineStart, lineEnd)
    }

    private fun allowParentScroll() {
        val parent: ViewParent? = parent
        parent?.requestDisallowInterceptTouchEvent(false)
    }
}
