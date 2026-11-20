package com.novelreader.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Owns the short-lived continue-reading banner shown once after opening the library. */
final class ResumeReadingPrompt {
    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View banner;
    private Runnable dismissTask;

    ResumeReadingPrompt(Activity activity) {
        this.activity = activity;
    }

    void show(FrameLayout root, View anchor, String title, String chapter,
              Typeface typeface, int surface, int text, int muted,
              int accent, int accentText, Runnable onContinue) {
        if (root == null || anchor == null || title == null || chapter == null) return;
        dismiss();
        float density = activity.getResources().getDisplayMetrics().density;

        LinearLayout container = new LinearLayout(activity);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        container.setPadding(dp(density, 14), dp(density, 10), dp(density, 14), dp(density, 10));
        container.setBackground(rounded(surface, 0x665D4775, dp(density, 16)));
        container.setElevation(dp(density, 10));

        FrameLayout close = new FrameLayout(activity);
        close.setContentDescription("\u0628\u0633\u062a\u0646 \u067e\u06cc\u0627\u0645 \u0627\u062f\u0627\u0645\u0647\u0654 \u0645\u0637\u0627\u0644\u0639\u0647");
        close.setClickable(true);
        close.setFocusable(true);
        NativeIconView closeIcon = new NativeIconView(activity, "close", muted);
        close.addView(closeIcon, new FrameLayout.LayoutParams(dp(density, 20), dp(density, 20), Gravity.CENTER));
        close.setOnClickListener(view -> dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(density, 38), dp(density, 38));
        closeParams.rightMargin = dp(density, 8);
        container.addView(close, closeParams);

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleView = label(title, typeface, text, 14);
        TextView chapterView = label(chapter, typeface, muted, 12);
        labels.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams chapterParams = new LinearLayout.LayoutParams(-1, -2);
        chapterParams.topMargin = dp(density, 3);
        labels.addView(chapterView, chapterParams);
        container.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        TextView continueButton = label("\u0627\u062f\u0627\u0645\u0647", typeface, accentText, 14);
        continueButton.setGravity(Gravity.CENTER);
        continueButton.setPadding(dp(density, 15), dp(density, 9), dp(density, 15), dp(density, 9));
        continueButton.setBackground(rounded(accent, accent, dp(density, 14)));
        continueButton.setOnClickListener(view -> onContinue.run());
        LinearLayout.LayoutParams continueParams = new LinearLayout.LayoutParams(-2, -2);
        continueParams.leftMargin = dp(density, 12);
        container.addView(continueButton, continueParams);

        int insertAt = Math.max(0, root.indexOfChild(anchor));
        FrameLayout.LayoutParams bannerParams = new FrameLayout.LayoutParams(-1, -2,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bannerParams.leftMargin = dp(density, 14);
        bannerParams.rightMargin = dp(density, 14);
        bannerParams.bottomMargin = dp(density, 12);
        root.addView(container, insertAt, bannerParams);
        banner = container;
        container.setAlpha(0f);
        container.setTranslationY(dp(density, 14));
        container.post(() -> container.animate().alpha(1f).translationY(0f).setDuration(180).start());

        dismissTask = this::dismiss;
        handler.postDelayed(dismissTask, 7000L);
    }

    void dismiss() {
        if (dismissTask != null) {
            handler.removeCallbacks(dismissTask);
            dismissTask = null;
        }
        View current = banner;
        banner = null;
        if (current == null) return;
        current.animate().cancel();
        current.animate().alpha(0f).translationY(dp(
                activity.getResources().getDisplayMetrics().density, 12)).setDuration(140)
                .withEndAction(() -> {
                    if (current.getParent() instanceof ViewGroup) {
                        ((ViewGroup) current.getParent()).removeView(current);
                    }
                }).start();
    }

    void dispose() {
        dismiss();
    }

    private TextView label(String value, Typeface typeface, int color, int size) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(typeface == null ? Typeface.DEFAULT : typeface);
        view.setMaxLines(1);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        view.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return view;
    }

    private GradientDrawable rounded(int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (stroke != 0) drawable.setStroke(dp(activity.getResources().getDisplayMetrics().density, 1), stroke);
        return drawable;
    }

    private static int dp(float density, float value) {
        return Math.round(density * value);
    }
}
