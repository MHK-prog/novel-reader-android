package com.novelreader.app

import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/** Appearance-only press feedback. It leaves click and gesture handling to the View. */
object TouchFeedback {
    @JvmStatic
    fun applyPressScale(view: View, pressedScale: Float) {
        val originalScaleX = view.scaleX
        val originalScaleY = view.scaleY
        var pressed = false

        view.setOnTouchListener { touchedView, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressed = true
                    animate(touchedView, originalScaleX, originalScaleY, pressedScale, true)
                }

                MotionEvent.ACTION_MOVE -> if (pressed && (
                    event.x < 0 || event.y < 0 ||
                        event.x > touchedView.width || event.y > touchedView.height
                    )
                ) {
                    pressed = false
                    animate(touchedView, originalScaleX, originalScaleY, pressedScale, false)
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_OUTSIDE -> if (pressed) {
                    pressed = false
                    animate(touchedView, originalScaleX, originalScaleY, pressedScale, false)
                }
            }
            false
        }
    }

    private fun animate(
        view: View,
        originalScaleX: Float,
        originalScaleY: Float,
        pressedScale: Float,
        isPressed: Boolean,
    ) {
        view.animate().cancel()
        if (isPressed) {
            view.animate()
                .scaleX(originalScaleX * pressedScale)
                .scaleY(originalScaleY * pressedScale)
                .setDuration(80)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            view.animate()
                .scaleX(originalScaleX)
                .scaleY(originalScaleY)
                .setDuration(150)
                .setInterpolator(OvershootInterpolator(1.15f))
                .start()
        }
    }
}
