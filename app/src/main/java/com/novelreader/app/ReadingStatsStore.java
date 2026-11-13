package com.novelreader.app;

import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Stores small hourly totals used by the local reading statistics page. */
final class ReadingStatsStore {
    static final int DAY = 0;
    static final int WEEK = 1;
    static final int MONTH = 2;
    static final int YEAR = 3;

    static final class Point {
        final String label;
        final long value;

        Point(String label, long value) {
            this.label = label;
            this.value = value;
        }
    }

    private static final String KEY = "hourly_totals";
    private static final String WORDS = "words";
    private static final String TIME = "millis";
    private static final SimpleDateFormat HOUR_FORMAT = new SimpleDateFormat("yyyyMMddHH", Locale.ROOT);
    private static final SimpleDateFormat WEEKDAY_FORMAT = new SimpleDateFormat("EEE", new Locale("fa"));
    private static final SimpleDateFormat MONTH_FORMAT = new SimpleDateFormat("LLL", new Locale("fa"));

    private final SharedPreferences preferences;
    private JSONObject buckets;

    ReadingStatsStore(SharedPreferences preferences) {
        this.preferences = preferences;
        try {
            buckets = new JSONObject(preferences.getString(KEY, "{}"));
        } catch (JSONException ignored) {
            buckets = new JSONObject();
        }
    }

    synchronized void addWords(long count, long timeMillis) {
        add(WORDS, count, timeMillis);
    }

    synchronized void addReadingMillis(long durationMillis, long timeMillis) {
        long remaining = durationMillis;
        long cursor = timeMillis;
        while (remaining > 0) {
            Calendar nextHour = Calendar.getInstance();
            nextHour.setTimeInMillis(cursor);
            nextHour.set(Calendar.MINUTE, 0);
            nextHour.set(Calendar.SECOND, 0);
            nextHour.set(Calendar.MILLISECOND, 0);
            nextHour.add(Calendar.HOUR_OF_DAY, 1);
            long chunk = Math.min(remaining, Math.max(1, nextHour.getTimeInMillis() - cursor));
            add(TIME, chunk, cursor);
            cursor += chunk;
            remaining -= chunk;
        }
    }

    private void add(String metric, long amount, long timeMillis) {
        if (amount <= 0) return;
        String key = HOUR_FORMAT.format(new Date(timeMillis));
        JSONObject hour = buckets.optJSONObject(key);
        if (hour == null) hour = new JSONObject();
        try {
            hour.put(metric, hour.optLong(metric, 0) + amount);
            buckets.put(key, hour);
        } catch (JSONException ignored) {
            return;
        }
        prune(timeMillis);
        preferences.edit().putString(KEY, buckets.toString()).apply();
    }

    private void prune(long nowMillis) {
        Calendar cutoff = Calendar.getInstance();
        cutoff.setTimeInMillis(nowMillis);
        cutoff.add(Calendar.DAY_OF_YEAR, -400);
        String cutoffKey = HOUR_FORMAT.format(cutoff.getTime());
        List<String> remove = new ArrayList<>();
        java.util.Iterator<String> keys = buckets.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.compareTo(cutoffKey) < 0) remove.add(key);
        }
        for (String key : remove) buckets.remove(key);
    }

    synchronized List<Point> points(int range, boolean words, long nowMillis) {
        List<Point> result = new ArrayList<>();
        String metric = words ? WORDS : TIME;
        Calendar cursor = Calendar.getInstance();
        cursor.setTimeInMillis(nowMillis);
        zeroTime(cursor);

        if (range == DAY) {
            for (int hour = 0; hour < 24; hour++) {
                Calendar current = (Calendar) cursor.clone();
                current.add(Calendar.HOUR_OF_DAY, hour);
                JSONObject bucket = buckets.optJSONObject(HOUR_FORMAT.format(current.getTime()));
                result.add(new Point(hour % 6 == 0 ? String.format(Locale.ROOT, "%02d", hour) : "",
                        bucket == null ? 0 : bucket.optLong(metric, 0)));
            }
            return result;
        }

        if (range == WEEK || range == MONTH) {
            int count = range == WEEK ? 7 : 30;
            cursor.add(Calendar.DAY_OF_YEAR, -(count - 1));
            for (int day = 0; day < count; day++) {
                Calendar current = (Calendar) cursor.clone();
                current.add(Calendar.DAY_OF_YEAR, day);
                String label = range == WEEK ? WEEKDAY_FORMAT.format(current.getTime())
                        : (day % 5 == 0 || day == count - 1
                        ? Integer.toString(current.get(Calendar.DAY_OF_MONTH)) : "");
                result.add(new Point(label, sumDay(current, metric)));
            }
            return result;
        }

        cursor.set(Calendar.DAY_OF_MONTH, 1);
        cursor.add(Calendar.MONTH, -11);
        for (int month = 0; month < 12; month++) {
            Calendar current = (Calendar) cursor.clone();
            current.add(Calendar.MONTH, month);
            result.add(new Point(MONTH_FORMAT.format(current.getTime()), sumMonth(current, metric)));
        }
        return result;
    }

    private long sumDay(Calendar day, String metric) {
        long total = 0;
        for (int hour = 0; hour < 24; hour++) {
            Calendar current = (Calendar) day.clone();
            current.add(Calendar.HOUR_OF_DAY, hour);
            JSONObject bucket = buckets.optJSONObject(HOUR_FORMAT.format(current.getTime()));
            if (bucket != null) total += bucket.optLong(metric, 0);
        }
        return total;
    }

    private long sumMonth(Calendar month, String metric) {
        Calendar current = (Calendar) month.clone();
        int count = current.getActualMaximum(Calendar.DAY_OF_MONTH);
        long total = 0;
        for (int day = 0; day < count; day++) {
            Calendar currentDay = (Calendar) current.clone();
            currentDay.add(Calendar.DAY_OF_MONTH, day);
            total += sumDay(currentDay, metric);
        }
        return total;
    }

    private void zeroTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }
}
