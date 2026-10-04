package com.novelreader.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Renders icons from the centralized Android vector assets in the root icons directory. */
final class NativeIconView extends View {
    private String name;
    private int tint;
    private boolean filled;
    private Drawable icon;

    NativeIconView(Context context, String name, int tint, boolean filled) {
        super(context);
        this.name = name;
        this.tint = tint;
        this.filled = filled;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        loadIcon();
    }

    void setStyle(int color, boolean isFilled) {
        tint = color;
        filled = isFilled;
        loadIcon();
    }

    void setName(String value) {
        name = value;
        loadIcon();
    }

    private void loadIcon() {
        String resourceName;
        switch (name) {
            case "menu": resourceName = "menu"; break;
            case "add": resourceName = "add"; break;
            case "delete": resourceName = "delete"; break;
            case "image": resourceName = "image"; break;
            case "settings": resourceName = "settings"; break;
            case "select": resourceName = "read_empty"; break;
            case "filter": resourceName = "filter"; break;
            case "back": resourceName = "arrow_prev"; break;
            case "next": resourceName = "arrow_next"; break;
            case "edit": resourceName = "pencil"; break;
            case "book": resourceName = "open_book"; break;
            case "tag": resourceName = "tag"; break;
            case "like": resourceName = filled ? "like_filled" : "like_empty"; break;
            case "dislike": resourceName = filled ? "dislike_filled" : "dislike_empty"; break;
            case "check": resourceName = "check"; break;
            case "close": resourceName = "close"; break;
            case "scrolltop": resourceName = "scroll_top"; break;
            case "scrollbottom": resourceName = "scroll_bottom"; break;
            case "folder": resourceName = "folder"; break;
            default: resourceName = name;
        }

        int resourceId = getResources().getIdentifier(resourceName, "drawable", getContext().getPackageName());
        Drawable loaded = resourceId == 0 ? null : getContext().getDrawable(resourceId);
        icon = loaded == null ? null : loaded.mutate();
        if (icon != null) icon.setTint(tint);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (icon == null) return;

        int maxSize = Math.min(getWidth(), getHeight());
        int intrinsicWidth = Math.max(1, icon.getIntrinsicWidth());
        int intrinsicHeight = Math.max(1, icon.getIntrinsicHeight());
        float scale = Math.min(maxSize / (float) intrinsicWidth, maxSize / (float) intrinsicHeight);
        int width = Math.round(intrinsicWidth * scale);
        int height = Math.round(intrinsicHeight * scale);
        int left = (getWidth() - width) / 2;
        int top = (getHeight() - height) / 2;
        icon.setBounds(left, top, left + width, top + height);
        icon.draw(canvas);
    }
}
