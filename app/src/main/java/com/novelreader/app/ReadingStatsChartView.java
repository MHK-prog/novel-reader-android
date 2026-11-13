package com.novelreader.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ReadingStatsChartView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<ReadingStatsStore.Point> points = new ArrayList<>();
    private boolean words;
    private int accentColor;
    private int mutedColor;
    private Typeface typeface;

    ReadingStatsChartView(Context context, int accentColor, int mutedColor, Typeface typeface) {
        super(context);
        this.accentColor = accentColor;
        this.mutedColor = mutedColor;
        this.typeface = typeface;
    }

    void setData(List<ReadingStatsStore.Point> points, boolean words, int accentColor,
                 int mutedColor, Typeface typeface) {
        this.points = points == null ? new ArrayList<>() : new ArrayList<>(points);
        this.words = words;
        this.accentColor = accentColor;
        this.mutedColor = mutedColor;
        this.typeface = typeface;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (points.isEmpty()) return;
        float density = getResources().getDisplayMetrics().density;
        float left = 46 * density, right = getWidth() - 9 * density;
        float top = 18 * density, bottom = getHeight() - 34 * density;
        if (right <= left || bottom <= top) return;

        long maximum = 0;
        for (ReadingStatsStore.Point point : points) maximum = Math.max(maximum, point.value);
        if (maximum == 0) maximum = words ? 1 : 60_000;

        paint.setTypeface(typeface == null ? Typeface.DEFAULT : typeface);
        paint.setTextSize(10 * density);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(mutedColor);
        for (int line = 0; line <= 4; line++) {
            float ratio = line / 4f;
            float y = bottom - (bottom - top) * ratio;
            paint.setColor(0x557F7887);
            paint.setStrokeWidth(density);
            canvas.drawLine(left, y, right, y, paint);
            paint.setColor(mutedColor);
            canvas.drawText(formatValue(maximum * line / 4.0, words), left - 6 * density,
                    y + 3 * density, paint);
        }

        float slotWidth = (right - left) / points.size();
        float barWidth = Math.max(2 * density, slotWidth * .58f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(accentColor);
        for (int i = 0; i < points.size(); i++) {
            float height = (float) (points.get(i).value / (double) maximum) * (bottom - top);
            float center = left + slotWidth * (i + .5f);
            RectF bar = new RectF(center - barWidth / 2, bottom - height,
                    center + barWidth / 2, bottom);
            canvas.drawRoundRect(bar, Math.min(barWidth / 2, 5 * density),
                    Math.min(barWidth / 2, 5 * density), paint);
        }

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(10 * density);
        paint.setColor(mutedColor);
        for (int i = 0; i < points.size(); i++) {
            String label = points.get(i).label;
            if (!label.isEmpty()) {
                canvas.drawText(label, left + slotWidth * (i + .5f),
                        getHeight() - 10 * density, paint);
            }
        }

        if (maximum == (words ? 1 : 60_000)) {
            paint.setTextSize(13 * density);
            canvas.drawText("هنوز آماری ثبت نشده", (left + right) / 2,
                    (top + bottom) / 2, paint);
        }
    }

    private String formatValue(double raw, boolean words) {
        if (!words) {
            double hours = raw / 3_600_000.0;
            return hours >= 1 ? String.format(Locale.ROOT, "%.0f", hours)
                    : String.format(Locale.ROOT, "%.0f", raw / 60_000.0);
        }
        if (raw >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", raw / 1_000_000.0);
        if (raw >= 1_000) return String.format(Locale.ROOT, "%.0fK", raw / 1_000.0);
        return String.format(Locale.ROOT, "%.0f", raw);
    }
}
