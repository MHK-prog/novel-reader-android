package com.novelreader.app;

import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/** Appearance-only press feedback. It leaves click and gesture handling to the View. */
final class TouchFeedback {
    private TouchFeedback() { }

    static void applyPressScale(View view, float pressedScale) {
        final float originalScaleX = view.getScaleX();
        final float originalScaleY = view.getScaleY();
        final boolean[] pressed = {false};
        view.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                pressed[0] = true;
                animate(v, originalScaleX, originalScaleY, pressedScale, true);
            } else if (action == MotionEvent.ACTION_MOVE && pressed[0]
                    && (event.getX() < 0 || event.getY() < 0
                    || event.getX() > v.getWidth() || event.getY() > v.getHeight())) {
                pressed[0] = false;
                animate(v, originalScaleX, originalScaleY, pressedScale, false);
            } else if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                    || action == MotionEvent.ACTION_OUTSIDE) && pressed[0]) {
                pressed[0] = false;
                animate(v, originalScaleX, originalScaleY, pressedScale, false);
            }
            return false;
        });
    }

    private static void animate(View view, float originalScaleX, float originalScaleY,
                                float pressedScale, boolean pressed) {
        view.animate().cancel();
        if (pressed) {
            view.animate().scaleX(originalScaleX * pressedScale)
                    .scaleY(originalScaleY * pressedScale).setDuration(80)
                    .setInterpolator(new DecelerateInterpolator()).start();
        } else {
            view.animate().scaleX(originalScaleX).scaleY(originalScaleY).setDuration(150)
                    .setInterpolator(new OvershootInterpolator(1.15f)).start();
        }
    }
}
