package com.novelreader.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.TreeMap;

final class ReadingStatsChartView extends View {
    private static final int[] BOOK_COLORS = {
            0xFF9B7BFF, 0xFF43A7F5, 0xFF4FC38B, 0xFFFF6B6B, 0xFFFFB454,
            0xFF4DC9C0, 0xFFED78B5, 0xFF8ABF4F, 0xFF7788FF, 0xFFCE8A55
    };
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<ReadingStatsStore.Point> points = new ArrayList<>();
    private boolean detailsVisible;
    private boolean showPercentages;
    private int accentColor;
    private int mutedColor;
    private Typeface typeface;
    private final Map<String, Integer> bookColors = new HashMap<>();

    ReadingStatsChartView(Context context, int accentColor, int mutedColor, Typeface typeface) {
        super(context);
        this.accentColor = accentColor;
        this.mutedColor = mutedColor;
        this.typeface = typeface;
    }

    void setData(List<ReadingStatsStore.Point> points, int accentColor,
                 int mutedColor, Typeface typeface, boolean detailsVisible, boolean showPercentages) {
        this.points = points == null ? new ArrayList<>() : new ArrayList<>(points);
        this.accentColor = accentColor;
        this.mutedColor = mutedColor;
        this.typeface = typeface;
        this.detailsVisible = detailsVisible;
        this.showPercentages = showPercentages;
        bookColors.clear();
        TreeSet<String> bookIds = new TreeSet<>();
        for (ReadingStatsStore.Point point : this.points) bookIds.addAll(point.byBook.keySet());
        int colorIndex = 0;
        for (String id : bookIds) {
            bookColors.put(id, BOOK_COLORS[colorIndex++ % BOOK_COLORS.length]);
        }
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
        if (maximum == 0) maximum = 60_000;

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
            canvas.drawText(formatValue(maximum * line / 4.0), left - 6 * density,
                    y + 3 * density, paint);
        }

        float slotWidth = (right - left) / points.size();
        float barWidth = Math.max(2 * density, slotWidth * .58f);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < points.size(); i++) {
            ReadingStatsStore.Point point = points.get(i);
            float center = left + slotWidth * (i + .5f);
            float totalHeight = (float) (point.value / (double) maximum) * (bottom - top);
            if (!detailsVisible || point.byBook.isEmpty()) {
                paint.setColor(accentColor);
                RectF bar = new RectF(center - barWidth / 2, bottom - totalHeight,
                        center + barWidth / 2, bottom);
                canvas.drawRoundRect(bar, Math.min(barWidth / 2, 5 * density),
                        Math.min(barWidth / 2, 5 * density), paint);
                continue;
            }
            Map<String, Long> segments = new TreeMap<>(point.byBook);
            long visibleTotal = 0;
            for (long value : segments.values()) visibleTotal += value;
            long remainder = Math.max(0, point.value - visibleTotal);
            float cursor = bottom;
            for (Map.Entry<String, Long> entry : segments.entrySet()) {
                float segmentHeight = (float) (entry.getValue() / (double) maximum) * (bottom - top);
                if (segmentHeight <= 0) continue;
                paint.setColor(colorForBook(entry.getKey()));
                RectF segment = new RectF(center - barWidth / 2, cursor - segmentHeight,
                        center + barWidth / 2, cursor);
                canvas.drawRect(segment, paint);
                if (showPercentages && point.value > 0 && segmentHeight >= 18 * density) {
                    paint.setColor(0xFFFFFFFF);
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setTextSize(9 * density);
                    int percent = (int) Math.round(entry.getValue() * 100.0 / point.value);
                    canvas.drawText(Integer.toString(percent), center,
                            cursor - segmentHeight / 2 + 3 * density, paint);
                }
                cursor -= segmentHeight;
            }
            if (remainder > 0) {
                float segmentHeight = (float) (remainder / (double) maximum) * (bottom - top);
                paint.setColor(0xFF77717D);
                canvas.drawRect(new RectF(center - barWidth / 2, cursor - segmentHeight,
                        center + barWidth / 2, cursor), paint);
                if (showPercentages && point.value > 0 && segmentHeight >= 18 * density) {
                    paint.setColor(Color.WHITE);
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setTextSize(9 * density);
                    canvas.drawText(Integer.toString((int) Math.round(remainder * 100.0 / point.value)), center,
                            cursor - segmentHeight / 2 + 3 * density, paint);
                }
            }
        }

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(10 * density);
        paint.setColor(mutedColor);
        for (int i = 0; i < points.size(); i++) {
            String label = points.get(i).label;
            if (!label.isEmpty()) canvas.drawText(label,
                    left + slotWidth * (i + .5f), getHeight() - 10 * density, paint);
        }

        if (maximum == 60_000) {
            paint.setTextSize(13 * density);
            paint.setColor(mutedColor);
            canvas.drawText("هنوز آماری ثبت نشده", (left + right) / 2,
                    (top + bottom) / 2, paint);
        }
    }

    int colorForBook(String id) {
        Integer color = bookColors.get(id);
        return color == null ? BOOK_COLORS[(id.hashCode() & 0x7FFFFFFF) % BOOK_COLORS.length] : color;
    }

    private String formatValue(double raw) {
        double hours = raw / 3_600_000.0;
        return hours >= 1 ? String.format(java.util.Locale.ROOT, "%.0f", hours)
                : String.format(java.util.Locale.ROOT, "%.0f", raw / 60_000.0);
    }
}
