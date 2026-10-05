package com.novelreader.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Renders icons from the centralized Android vector assets in the root icons directory. */
final class NativeIconView extends View {
    private String name;
    private int tint;
    private Drawable icon;

    NativeIconView(Context context, String name, int tint) {
        super(context);
        this.name = name;
        this.tint = tint;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        loadIcon();
    }

    void setTint(int color) {
        tint = color;
        loadIcon();
    }

    void setName(String value) {
        name = value;
        loadIcon();
    }

    private void loadIcon() {
        String resourceName;
        float rotation = 0f;
        switch (name) {
            case "menu": resourceName = "menu"; break;
            case "add": resourceName = "add"; break;
            case "delete": resourceName = "delete"; break;
            case "image": resourceName = "image"; break;
            case "settings": resourceName = "settings"; break;
            case "select": resourceName = "read_empty"; break;
            case "filter": resourceName = "filter"; break;
            case "back": resourceName = "direction"; rotation = 180f; break;
            case "next": resourceName = "direction"; break;
            case "edit": resourceName = "pencil"; break;
            case "book": resourceName = "open_book"; break;
            case "tag": resourceName = "tag"; break;
            case "check": resourceName = "check"; break;
            case "close": resourceName = "close"; break;
            case "scrolltop": resourceName = "direction"; rotation = 270f; break;
            case "scrollbottom": resourceName = "direction"; rotation = 90f; break;
            case "folder": resourceName = "folder"; break;
            default: resourceName = name;
        }

        int resourceId = getResources().getIdentifier(resourceName, "drawable", getContext().getPackageName());
        Drawable loaded = resourceId == 0 ? null : getContext().getDrawable(resourceId);
        icon = loaded == null ? null : loaded.mutate();
        if (icon != null) icon.setTint(tint);
        setRotation(rotation);
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
