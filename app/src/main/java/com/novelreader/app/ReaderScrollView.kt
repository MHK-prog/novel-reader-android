package com.novelreader.app

import android.content.Context
import android.view.MotionEvent
import android.widget.ScrollView
import kotlin.math.abs

fun interface QuickScrollCallback {
    fun show(toTop: Boolean)
}

class ReaderScrollView(
    context: Context,
    private val quickScrollCallback: QuickScrollCallback
) : ScrollView(context) {
    private var startY = 0f
    private var startAt = 0L
    private var lastFlickAt = 0L
    private var lastFlickDirection = 0
    private var flickCount = 0

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = event.y
                startAt = event.eventTime
            }
            MotionEvent.ACTION_UP -> {
                val distance = event.y - startY
                val duration = event.eventTime - startAt
                val direction = if (distance < 0f) 1 else -1
                if (abs(distance) >= dp(72f) && duration <= 360L) {
                    val now = event.eventTime
                    flickCount = if (direction == lastFlickDirection && now - lastFlickAt <= 1000L) {
                        flickCount + 1
                    } else {
                        1
                    }
                    lastFlickDirection = direction
                    lastFlickAt = now
                    if (flickCount >= 2) {
                        quickScrollCallback.show(direction < 0)
                        flickCount = 0
                        lastFlickDirection = 0
                    }
                } else {
                    flickCount = 0
                    lastFlickDirection = 0
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                flickCount = 0
                lastFlickDirection = 0
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
