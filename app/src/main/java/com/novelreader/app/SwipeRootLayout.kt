package com.novelreader.app

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.max

/** Root touch dispatcher that keeps drawer movement attached to the user's finger. */
class SwipeRootLayout(
    context: Context,
    private val callbacks: Callbacks,
    private val edgeSizePx: Int,
    private val minDistancePx: Int,
    private val minVelocityPx: Int,
    private val maxOffPathPx: Int,
    private val touchSlopPx: Int
) : FrameLayout(context) {

    interface Callbacks {
        fun drawerSwipeAvailable(): Boolean
        fun drawerIsOpen(): Boolean
        fun drawerWidth(): Int
        fun openDrawer()
        fun beginDrawerGesture(progress: Float)
        fun updateDrawerGesture(progress: Float)
        fun settleDrawerGesture(open: Boolean)
    }

    private var drawerLayer: View? = null
    private var drawerPanel: View? = null
    private var startX = 0f
    private var startY = 0f
    private var startProgress = 0f
    private var lastProgress = 0f
    private var gestureCandidate = false
    private var trackingDrawer = false

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                first: MotionEvent?,
                last: MotionEvent?,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (first == null || last == null || !callbacks.drawerSwipeAvailable()) return false
                val horizontal = first.x - last.x
                val vertical = abs(first.y - last.y)
                val startedAtRightEdge = first.x >= width - edgeSizePx
                if (startedAtRightEdge && horizontal > minDistancePx &&
                    abs(velocityX) > minVelocityPx && velocityX < 0f &&
                    vertical < maxOffPathPx
                ) {
                    callbacks.openDrawer()
                    return true
                }
                return false
            }
        })

    fun attachDrawerViews(layer: View, panel: View) {
        drawerLayer = layer
        drawerPanel = panel
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val layer = drawerLayer
        val panel = drawerPanel
        if (layer == null || panel == null) return super.dispatchTouchEvent(event)

        val action = event.actionMasked
        gestureDetector.onTouchEvent(event)
        if (action == MotionEvent.ACTION_DOWN) {
            startX = event.x
            startY = event.y
            val drawerWidth = max(1, callbacks.drawerWidth()).toFloat()
            val drawerVisible = layer.visibility == View.VISIBLE
            startProgress = if (drawerVisible) {
                (1f - panel.translationX / drawerWidth).coerceIn(0f, 1f)
            } else {
                if (callbacks.drawerIsOpen()) 1f else 0f
            }
            gestureCandidate = (drawerVisible && startProgress > 0f) ||
                (callbacks.drawerSwipeAvailable() && startX >= width - edgeSizePx)
            trackingDrawer = false
            lastProgress = startProgress
            return super.dispatchTouchEvent(event)
        }

        if (!trackingDrawer && action == MotionEvent.ACTION_MOVE && gestureCandidate) {
            val dx = event.x - startX
            val dy = event.y - startY
            val opening = dx < 0f && startProgress < 1f
            val closing = dx > 0f && startProgress > 0f
            if (abs(dx) > touchSlopPx && abs(dx) > abs(dy) * 1.15f && (opening || closing)) {
                trackingDrawer = true
                callbacks.beginDrawerGesture(startProgress)
                val cancel = MotionEvent.obtain(event)
                cancel.action = MotionEvent.ACTION_CANCEL
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
                lastProgress = startProgress - dx / max(1, callbacks.drawerWidth()).toFloat()
                callbacks.updateDrawerGesture(lastProgress)
                return true
            }
        }

        if (trackingDrawer) {
            when (action) {
                MotionEvent.ACTION_MOVE -> {
                    lastProgress = startProgress -
                        (event.x - startX) / max(1, callbacks.drawerWidth()).toFloat()
                    callbacks.updateDrawerGesture(lastProgress)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    trackingDrawer = false
                    gestureCandidate = false
                    callbacks.settleDrawerGesture(lastProgress >= 0.5f)
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    trackingDrawer = false
                    gestureCandidate = false
                    callbacks.settleDrawerGesture(startProgress >= 0.5f)
                    return true
                }
                else -> return true
            }
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            gestureCandidate = false
        }
        return super.dispatchTouchEvent(event)
    }
}
