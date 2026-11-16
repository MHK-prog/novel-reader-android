package com.novelreader.app;

import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Small local hourly totals with optional per-book breakdowns. */
final class ReadingStatsStore {
    static final int DAY = 0;
    static final int WEEK = 1;
    static final int MONTH = 2;
    static final int YEAR = 3;

    static final class Point {
        final String label;
        final long value;
        final Map<String, Long> byBook;

        Point(String label, long value, Map<String, Long> byBook) {
            this.label = label;
            this.value = value;
            this.byBook = byBook == null ? Collections.emptyMap() : byBook;
        }
    }

    static final class JalaliDate {
        final int year;
        final int month;
        final int day;

        JalaliDate(int year, int month, int day) {
            this.year = year;
            this.month = month;
            this.day = day;
        }
    }

    private static final String KEY = "hourly_totals";
    private static final String WORDS = "words";
    private static final String TIME = "millis";
    private static final String BOOKS = "byBook";
    private static final long PERSIST_INTERVAL_MS = 5_000L;
    private static final int[] JALALI_BREAKS = {
            -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181,
            1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    };
    static final String[] JALALI_MONTHS = {
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    };

    private final SharedPreferences preferences;
    private JSONObject buckets;
    private long cachedWords;
    private long cachedMillis;
    private long lastPersistAt;
    private boolean dirty;

    ReadingStatsStore(SharedPreferences preferences) {
        this.preferences = preferences;
        try {
            buckets = new JSONObject(preferences.getString(KEY, "{}"));
        } catch (JSONException ignored) {
            buckets = new JSONObject();
        }
        refreshTotals();
    }

    synchronized void addWords(long count, long timeMillis) {
        add(WORDS, null, count, timeMillis);
    }

    synchronized void addWords(String bookId, long count, long timeMillis) {
        add(WORDS, bookId, count, timeMillis);
    }

    synchronized void addReadingMillis(long durationMillis, long timeMillis) {
        addReadingMillis(null, durationMillis, timeMillis);
    }

    synchronized void addReadingMillis(String bookId, long durationMillis, long timeMillis) {
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
            add(TIME, bookId, chunk, cursor);
            cursor += chunk;
            remaining -= chunk;
        }
    }

    private void add(String metric, String bookId, long amount, long timeMillis) {
        if (amount <= 0) return;
        String key = hourKey(timeMillis);
        JSONObject hour = buckets.optJSONObject(key);
        if (hour == null) hour = new JSONObject();
        try {
            hour.put(metric, hour.optLong(metric, 0) + amount);
            if (bookId != null && !bookId.isEmpty()) {
                JSONObject byBook = hour.optJSONObject(BOOKS);
                if (byBook == null) byBook = new JSONObject();
                JSONObject book = byBook.optJSONObject(bookId);
                if (book == null) book = new JSONObject();
                book.put(metric, book.optLong(metric, 0) + amount);
                byBook.put(bookId, book);
                hour.put(BOOKS, byBook);
            }
            buckets.put(key, hour);
        } catch (JSONException ignored) {
            return;
        }
        if (WORDS.equals(metric)) cachedWords += amount;
        else cachedMillis += amount;
        dirty = true;
        prune(timeMillis);
        long now = System.currentTimeMillis();
        if (lastPersistAt == 0 || now - lastPersistAt >= PERSIST_INTERVAL_MS) persist();
    }

    synchronized long totalWords() { return cachedWords; }

    synchronized long totalReadingMillis() { return cachedMillis; }

    synchronized long totalForCurrentYear(boolean words, long nowMillis) {
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(nowMillis);
        JalaliDate jalali = fromGregorian(today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1,
                today.get(Calendar.DAY_OF_MONTH));
        int[] startDate = toGregorian(jalali.year, 1, 1);
        int[] endDate = toGregorian(jalali.year + 1, 1, 1);
        String startKey = hourKey(calendar(startDate[0], startDate[1], startDate[2]).getTimeInMillis());
        String endKey = hourKey(calendar(endDate[0], endDate[1], endDate[2]).getTimeInMillis());
        String metric = words ? WORDS : TIME;
        long total = 0;
        java.util.Iterator<String> keys = buckets.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.compareTo(startKey) < 0 || key.compareTo(endKey) >= 0) continue;
            JSONObject hour = buckets.optJSONObject(key);
            if (hour != null) total += hour.optLong(metric, 0);
        }
        return total;
    }

    /** Persist accumulated scroll and timer updates without serializing the full history on every event. */
    synchronized void persist() {
        if (!dirty) return;
        preferences.edit().putString(KEY, buckets.toString()).apply();
        lastPersistAt = System.currentTimeMillis();
        dirty = false;
    }

    private void refreshTotals() {
        cachedWords = 0;
        cachedMillis = 0;
        java.util.Iterator<String> keys = buckets.keys();
        while (keys.hasNext()) {
            JSONObject hour = buckets.optJSONObject(keys.next());
            if (hour == null) continue;
            cachedWords += hour.optLong(WORDS, 0);
            cachedMillis += hour.optLong(TIME, 0);
        }
    }

    synchronized long totalForBook(String bookId, boolean words) {
        if (bookId == null || bookId.isEmpty()) return 0;
        String metric = words ? WORDS : TIME;
        long total = 0;
        java.util.Iterator<String> keys = buckets.keys();
        while (keys.hasNext()) {
            JSONObject hour = buckets.optJSONObject(keys.next());
            JSONObject book = hour == null ? null : hour.optJSONObject(BOOKS);
            JSONObject values = book == null ? null : book.optJSONObject(bookId);
            if (values != null) total += values.optLong(metric, 0);
        }
        return total;
    }

    synchronized List<Point> points(int range, boolean words, long nowMillis) {
        return points(range, words, nowMillis, 0);
    }

    synchronized List<Point> points(int range, boolean words, long nowMillis, int periodOffset) {
        List<Point> result = new ArrayList<>();
        String metric = words ? WORDS : TIME;
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(nowMillis);
        zeroTime(today);
        if (range == DAY) {
            today.add(Calendar.DAY_OF_YEAR, -Math.max(0, periodOffset));
            for (int hour = 0; hour < 24; hour++) {
                Calendar current = (Calendar) today.clone();
                current.add(Calendar.HOUR_OF_DAY, hour);
                Map<String, Long> byBook = hourValues(current.getTimeInMillis(), metric);
                long total = total(current.getTimeInMillis(), metric);
                addLegacySegment(byBook, total);
                String label = hour % 6 == 0 ? String.format(Locale.ROOT, "%02d", hour) : "";
                result.add(new Point(label, total, byBook));
            }
            return result;
        }
        if (range == WEEK) {
            today.add(Calendar.DAY_OF_YEAR, -7 * Math.max(0, periodOffset));
            int daysSinceSaturday = (today.get(Calendar.DAY_OF_WEEK) - Calendar.SATURDAY + 7) % 7;
            today.add(Calendar.DAY_OF_YEAR, -daysSinceSaturday);
            for (int day = 0; day < 7; day++) {
                Calendar current = (Calendar) today.clone();
                current.add(Calendar.DAY_OF_YEAR, day);
                JalaliDate jalali = fromGregorian(current.get(Calendar.YEAR), current.get(Calendar.MONTH) + 1,
                        current.get(Calendar.DAY_OF_MONTH));
                Map<String, Long> byBook = dayValues(current, metric);
                long total = sumDay(current, metric);
                addLegacySegment(byBook, total);
                result.add(new Point(Integer.toString(jalali.day), total, byBook));
            }
            return result;
        }
        JalaliDate currentJalali = fromGregorian(today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1,
                today.get(Calendar.DAY_OF_MONTH));
        if (range == MONTH) {
            JalaliDate month = shiftMonth(currentJalali, -Math.max(0, periodOffset));
            int count = monthLength(month.year, month.month);
            int[] first = toGregorian(month.year, month.month, 1);
            Calendar start = calendar(first[0], first[1], first[2]);
            for (int day = 0; day < count; day++) {
                Calendar current = (Calendar) start.clone();
                current.add(Calendar.DAY_OF_YEAR, day);
                Map<String, Long> byBook = dayValues(current, metric);
                long total = sumDay(current, metric);
                addLegacySegment(byBook, total);
                String label = day % 5 == 0 || day == count - 1
                        ? Integer.toString(day + 1) : "";
                result.add(new Point(label, total, byBook));
            }
            return result;
        }
        int year = currentJalali.year - Math.max(0, periodOffset);
        for (int month = 1; month <= 12; month++) {
            int[] first = toGregorian(year, month, 1);
            int[] next = month == 12 ? toGregorian(year + 1, 1, 1) : toGregorian(year, month + 1, 1);
            Calendar start = calendar(first[0], first[1], first[2]);
            Calendar end = calendar(next[0], next[1], next[2]);
            Map<String, Long> byBook = rangeValues(start, end, metric);
            long total = sumRange(start, end, metric);
            addLegacySegment(byBook, total);
            result.add(new Point(JALALI_MONTHS[month - 1], total, byBook));
        }
        return result;
    }

    synchronized String periodLabel(int range, long nowMillis, int periodOffset) {
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(nowMillis);
        zeroTime(today);
        if (range == DAY) {
            today.add(Calendar.DAY_OF_YEAR, -Math.max(0, periodOffset));
            return jalaliLabel(today);
        }
        if (range == WEEK) {
            today.add(Calendar.DAY_OF_YEAR, -7 * Math.max(0, periodOffset));
            int daysSinceSaturday = (today.get(Calendar.DAY_OF_WEEK) - Calendar.SATURDAY + 7) % 7;
            today.add(Calendar.DAY_OF_YEAR, -daysSinceSaturday);
            Calendar end = (Calendar) today.clone();
            end.add(Calendar.DAY_OF_YEAR, 6);
            return jalaliLabel(today) + " – " + jalaliLabel(end);
        }
        JalaliDate current = fromGregorian(today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1,
                today.get(Calendar.DAY_OF_MONTH));
        if (range == MONTH) {
            JalaliDate date = shiftMonth(current, -Math.max(0, periodOffset));
            return JALALI_MONTHS[date.month - 1] + " " + date.year;
        }
        return Integer.toString(current.year - Math.max(0, periodOffset));
    }

    private String jalaliLabel(Calendar calendar) {
        JalaliDate date = fromGregorian(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));
        return date.year + "/" + twoDigits(date.month) + "/" + twoDigits(date.day);
    }

    private String twoDigits(int value) { return value < 10 ? "0" + value : Integer.toString(value); }

    private long total(long timeMillis, String metric) {
        JSONObject hour = buckets.optJSONObject(hourKey(timeMillis));
        return hour == null ? 0 : hour.optLong(metric, 0);
    }

    private Map<String, Long> hourValues(long timeMillis, String metric) {
        JSONObject hour = buckets.optJSONObject(hourKey(timeMillis));
        JSONObject books = hour == null ? null : hour.optJSONObject(BOOKS);
        Map<String, Long> result = new HashMap<>();
        if (books == null) return result;
        java.util.Iterator<String> ids = books.keys();
        while (ids.hasNext()) {
            String id = ids.next();
            JSONObject values = books.optJSONObject(id);
            long value = values == null ? 0 : values.optLong(metric, 0);
            if (value > 0) result.put(id, value);
        }
        return result;
    }

    private Map<String, Long> dayValues(Calendar day, String metric) {
        Map<String, Long> result = new HashMap<>();
        for (int hour = 0; hour < 24; hour++) {
            Calendar current = (Calendar) day.clone();
            current.add(Calendar.HOUR_OF_DAY, hour);
            merge(result, hourValues(current.getTimeInMillis(), metric));
        }
        return result;
    }

    private Map<String, Long> rangeValues(Calendar start, Calendar end, String metric) {
        Map<String, Long> result = new HashMap<>();
        Calendar current = (Calendar) start.clone();
        while (current.before(end)) {
            merge(result, dayValues(current, metric));
            current.add(Calendar.DAY_OF_YEAR, 1);
        }
        return result;
    }

    private void merge(Map<String, Long> into, Map<String, Long> values) {
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            Long previous = into.get(entry.getKey());
            into.put(entry.getKey(), (previous == null ? 0 : previous) + entry.getValue());
        }
    }

    private void addLegacySegment(Map<String, Long> values, long total) {
        long remainder = Math.max(0, total - sumValues(values));
        if (remainder > 0) values.put("__legacy__", remainder);
    }

    private long sumValues(Map<String, Long> values) {
        long sum = 0;
        for (long value : values.values()) sum += value;
        return sum;
    }

    private long sumDay(Calendar day, String metric) {
        long total = 0;
        for (int hour = 0; hour < 24; hour++) {
            Calendar current = (Calendar) day.clone();
            current.add(Calendar.HOUR_OF_DAY, hour);
            total += total(current.getTimeInMillis(), metric);
        }
        return total;
    }

    private long sumRange(Calendar start, Calendar end, String metric) {
        long total = 0;
        Calendar current = (Calendar) start.clone();
        while (current.before(end)) {
            total += sumDay(current, metric);
            current.add(Calendar.DAY_OF_YEAR, 1);
        }
        return total;
    }

    private void prune(long nowMillis) {
        Calendar cutoff = Calendar.getInstance();
        cutoff.setTimeInMillis(nowMillis);
        cutoff.add(Calendar.DAY_OF_YEAR, -400);
        String cutoffKey = hourKey(cutoff.getTimeInMillis());
        List<String> remove = new ArrayList<>();
        java.util.Iterator<String> keys = buckets.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.compareTo(cutoffKey) < 0) remove.add(key);
        }
        for (String key : remove) {
            JSONObject hour = buckets.optJSONObject(key);
            if (hour != null) {
                cachedWords -= hour.optLong(WORDS, 0);
                cachedMillis -= hour.optLong(TIME, 0);
            }
            buckets.remove(key);
        }
    }

    private String hourKey(long timeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMillis);
        return String.format(Locale.ROOT, "%04d%02d%02d%02d", calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.HOUR_OF_DAY));
    }

    private void zeroTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private Calendar calendar(int year, int month, int day) {
        Calendar result = Calendar.getInstance();
        result.clear();
        result.set(year, month - 1, day, 0, 0, 0);
        return result;
    }

    private JalaliDate shiftMonth(JalaliDate date, int delta) {
        int monthIndex = date.year * 12 + date.month - 1 + delta;
        int year = Math.floorDiv(monthIndex, 12);
        int month = Math.floorMod(monthIndex, 12) + 1;
        return new JalaliDate(year, month, Math.min(date.day, monthLength(year, month)));
    }

    private int monthLength(int year, int month) {
        if (month <= 6) return 31;
        if (month <= 11) return 30;
        int[] start = toGregorian(year, 1, 1);
        int[] next = toGregorian(year + 1, 1, 1);
        return dayNumber(next[0], next[1], next[2]) - dayNumber(start[0], start[1], start[2]) == 366 ? 30 : 29;
    }

    private JalaliDate fromGregorian(int gy, int gm, int gd) {
        int jy = gy - 621;
        int[] start = toGregorian(jy, 1, 1);
        int currentDay = dayNumber(gy, gm, gd);
        if (currentDay < dayNumber(start[0], start[1], start[2])) {
            jy--;
            start = toGregorian(jy, 1, 1);
        }
        int dayOfYear = currentDay - dayNumber(start[0], start[1], start[2]);
        if (dayOfYear < 186) return new JalaliDate(jy, dayOfYear / 31 + 1, dayOfYear % 31 + 1);
        int remaining = dayOfYear - 186;
        return new JalaliDate(jy, remaining / 30 + 7, remaining % 30 + 1);
    }

    private int[] toGregorian(int jy, int jm, int jd) {
        int gy = jy + 621;
        int march = jalaliMarch(jy);
        Calendar start = calendar(gy, 3, march);
        int offset = jm <= 6 ? (jm - 1) * 31 + jd - 1 : 186 + (jm - 7) * 30 + jd - 1;
        start.add(Calendar.DAY_OF_YEAR, offset);
        return new int[]{start.get(Calendar.YEAR), start.get(Calendar.MONTH) + 1, start.get(Calendar.DAY_OF_MONTH)};
    }

    private int jalaliMarch(int jy) {
        if (jy < JALALI_BREAKS[0] || jy >= JALALI_BREAKS[JALALI_BREAKS.length - 1]) {
            return 20;
        }
        int gy = jy + 621;
        int leapJ = -14;
        int jp = JALALI_BREAKS[0];
        int jump = 0;
        for (int i = 1; i < JALALI_BREAKS.length; i++) {
            int jm = JALALI_BREAKS[i];
            jump = jm - jp;
            if (jy < jm) break;
            leapJ += (jump / 33) * 8 + (jump % 33) / 4;
            jp = jm;
        }
        int n = jy - jp;
        leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4;
        if (jump % 33 == 4 && jump - n == 4) leapJ++;
        int leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150;
        return 20 + leapJ - leapG;
    }

    private int dayNumber(int year, int month, int day) {
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT);
        calendar.clear();
        calendar.set(year, month - 1, day, 0, 0, 0);
        return (int) (calendar.getTimeInMillis() / 86_400_000L);
    }
}
