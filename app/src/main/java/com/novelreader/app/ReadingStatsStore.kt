package com.novelreader.app

import android.content.SharedPreferences
import org.json.JSONException
import org.json.JSONObject
import java.util.ArrayList
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.HashMap
import java.util.Locale
import java.util.TimeZone

/** Small local hourly totals with optional per-book breakdowns. */
class ReadingStatsStore(private val preferences: SharedPreferences) {
    class Point(label: String, value: Long, byBook: Map<String, Long>?) {
        @JvmField val label: String = label
        @JvmField val value: Long = value
        @JvmField val byBook: Map<String, Long> = byBook ?: emptyMap()
    }

    class JalaliDate(@JvmField val year: Int, @JvmField val month: Int, @JvmField val day: Int)

    private var buckets: JSONObject = try {
        JSONObject(preferences.getString(KEY, "{}") ?: "{}")
    } catch (_: JSONException) {
        JSONObject()
    }
    private var cachedMillis = 0L
    private var lastPersistAt = 0L
    private var dirty = false

    init {
        refreshTotals()
    }

    @Synchronized
    fun addReadingMillis(bookId: String?, durationMillis: Long, timeMillis: Long) {
        var remaining = durationMillis
        var cursor = timeMillis
        while (remaining > 0) {
            val nextHour = Calendar.getInstance().apply {
                timeInMillis = cursor
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.HOUR_OF_DAY, 1)
            }
            val chunk = minOf(remaining, maxOf(1L, nextHour.timeInMillis - cursor))
            add(bookId, chunk, cursor)
            cursor += chunk
            remaining -= chunk
        }
    }

    private fun add(bookId: String?, amount: Long, timeMillis: Long) {
        if (amount <= 0) return
        val key = hourKey(timeMillis)
        val hour = buckets.optJSONObject(key) ?: JSONObject()
        try {
            hour.put(TIME, hour.optLong(TIME, 0) + amount)
            if (!bookId.isNullOrEmpty()) {
                val byBook = hour.optJSONObject(BOOKS) ?: JSONObject()
                val book = byBook.optJSONObject(bookId) ?: JSONObject()
                book.put(TIME, book.optLong(TIME, 0) + amount)
                byBook.put(bookId, book)
                hour.put(BOOKS, byBook)
            }
            buckets.put(key, hour)
        } catch (_: JSONException) {
            return
        }
        cachedMillis += amount
        dirty = true
        prune(timeMillis)
        val now = System.currentTimeMillis()
        if (lastPersistAt == 0L || now - lastPersistAt >= PERSIST_INTERVAL_MS) persist()
    }

    @Synchronized
    fun totalReadingMillis(): Long = cachedMillis

    /** Persist accumulated scroll and timer updates without serializing the full history on every event. */
    @Synchronized
    fun persist() {
        if (!dirty) return
        preferences.edit().putString(KEY, buckets.toString()).apply()
        lastPersistAt = System.currentTimeMillis()
        dirty = false
    }

    private fun refreshTotals() {
        cachedMillis = 0
        val keys = buckets.keys()
        while (keys.hasNext()) {
            val hour = buckets.optJSONObject(keys.next()) ?: continue
            cachedMillis += hour.optLong(TIME, 0)
        }
    }

    @Synchronized
    fun totalForBook(bookId: String?): Long {
        if (bookId.isNullOrEmpty()) return 0
        var total = 0L
        val keys = buckets.keys()
        while (keys.hasNext()) {
            val hour = buckets.optJSONObject(keys.next())
            val book = hour?.optJSONObject(BOOKS)?.optJSONObject(bookId)
            if (book != null) total += book.optLong(TIME, 0)
        }
        return total
    }

    @Synchronized
    fun points(range: Int, nowMillis: Long, periodOffset: Int): List<Point> {
        val result = ArrayList<Point>()
        val today = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            zeroTime(this)
        }
        if (range == DAY) {
            today.add(Calendar.DAY_OF_YEAR, -maxOf(0, periodOffset))
            for (hour in 0 until 24) {
                val current = (today.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, hour) }
                val values = hourValues(current.timeInMillis)
                val total = total(current.timeInMillis)
                val label = if (hour % 6 == 0) String.format(Locale.ROOT, "%02d", hour) else ""
                result.add(Point(label, total, values))
            }
            return result
        }
        if (range == WEEK) {
            today.add(Calendar.DAY_OF_YEAR, -7 * maxOf(0, periodOffset))
            val daysSinceSaturday = (today.get(Calendar.DAY_OF_WEEK) - Calendar.SATURDAY + 7) % 7
            today.add(Calendar.DAY_OF_YEAR, -daysSinceSaturday)
            for (day in 0 until 7) {
                val current = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, day) }
                val jalali = fromGregorian(
                    current.get(Calendar.YEAR), current.get(Calendar.MONTH) + 1,
                    current.get(Calendar.DAY_OF_MONTH),
                )
                result.add(Point(jalali.day.toString(), sumDay(current), dayValues(current)))
            }
            return result
        }

        val currentJalali = fromGregorian(
            today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1, today.get(Calendar.DAY_OF_MONTH),
        )
        if (range == MONTH) {
            val month = shiftMonth(currentJalali, -maxOf(0, periodOffset))
            val count = monthLength(month.year, month.month)
            val first = toGregorian(month.year, month.month, 1)
            val start = calendar(first[0], first[1], first[2])
            for (day in 0 until count) {
                val current = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, day) }
                val label = if (day % 5 == 0 || day == count - 1) (day + 1).toString() else ""
                result.add(Point(label, sumDay(current), dayValues(current)))
            }
            return result
        }

        val year = currentJalali.year - maxOf(0, periodOffset)
        for (month in 1..12) {
            val first = toGregorian(year, month, 1)
            val next = if (month == 12) toGregorian(year + 1, 1, 1)
            else toGregorian(year, month + 1, 1)
            val start = calendar(first[0], first[1], first[2])
            val end = calendar(next[0], next[1], next[2])
            result.add(Point(JALALI_MONTHS[month - 1], sumRange(start, end), rangeValues(start, end)))
        }
        return result
    }

    @Synchronized
    fun periodLabel(range: Int, nowMillis: Long, periodOffset: Int): String {
        val today = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            zeroTime(this)
        }
        if (range == DAY) {
            today.add(Calendar.DAY_OF_YEAR, -maxOf(0, periodOffset))
            return jalaliLabel(today)
        }
        if (range == WEEK) {
            today.add(Calendar.DAY_OF_YEAR, -7 * maxOf(0, periodOffset))
            val daysSinceSaturday = (today.get(Calendar.DAY_OF_WEEK) - Calendar.SATURDAY + 7) % 7
            today.add(Calendar.DAY_OF_YEAR, -daysSinceSaturday)
            val end = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 6) }
            return "${jalaliLabel(today)} – ${jalaliLabel(end)}"
        }
        val current = fromGregorian(
            today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1, today.get(Calendar.DAY_OF_MONTH),
        )
        if (range == MONTH) {
            val date = shiftMonth(current, -maxOf(0, periodOffset))
            return "${JALALI_MONTHS[date.month - 1]} ${date.year}"
        }
        return (current.year - maxOf(0, periodOffset)).toString()
    }

    private fun jalaliLabel(calendar: Calendar): String {
        val date = fromGregorian(
            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH),
        )
        return "${date.year}/${twoDigits(date.month)}/${twoDigits(date.day)}"
    }

    private fun twoDigits(value: Int): String = if (value < 10) "0$value" else value.toString()

    private fun total(timeMillis: Long): Long = buckets.optJSONObject(hourKey(timeMillis))?.optLong(TIME, 0) ?: 0

    private fun hourValues(timeMillis: Long): Map<String, Long> {
        val books = buckets.optJSONObject(hourKey(timeMillis))?.optJSONObject(BOOKS) ?: return HashMap()
        val result = HashMap<String, Long>()
        val ids = books.keys()
        while (ids.hasNext()) {
            val id = ids.next()
            val value = books.optJSONObject(id)?.optLong(TIME, 0) ?: 0
            if (value > 0) result[id] = value
        }
        return result
    }

    private fun dayValues(day: Calendar): Map<String, Long> {
        val result = HashMap<String, Long>()
        for (hour in 0 until 24) {
            val current = (day.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, hour) }
            merge(result, hourValues(current.timeInMillis))
        }
        return result
    }

    private fun rangeValues(start: Calendar, end: Calendar): Map<String, Long> {
        val result = HashMap<String, Long>()
        val current = start.clone() as Calendar
        while (current.before(end)) {
            merge(result, dayValues(current))
            current.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    private fun merge(into: MutableMap<String, Long>, values: Map<String, Long>) {
        for ((key, value) in values) into[key] = (into[key] ?: 0) + value
    }

    private fun sumDay(day: Calendar): Long {
        var total = 0L
        for (hour in 0 until 24) {
            val current = (day.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, hour) }
            total += total(current.timeInMillis)
        }
        return total
    }

    private fun sumRange(start: Calendar, end: Calendar): Long {
        var total = 0L
        val current = start.clone() as Calendar
        while (current.before(end)) {
            total += sumDay(current)
            current.add(Calendar.DAY_OF_YEAR, 1)
        }
        return total
    }

    private fun prune(nowMillis: Long) {
        val cutoff = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, -400)
        }
        val cutoffKey = hourKey(cutoff.timeInMillis)
        val remove = ArrayList<String>()
        val keys = buckets.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key < cutoffKey) remove.add(key)
        }
        for (key in remove) {
            cachedMillis -= buckets.optJSONObject(key)?.optLong(TIME, 0) ?: 0
            buckets.remove(key)
        }
    }

    private fun hourKey(timeMillis: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = timeMillis }
        return String.format(
            Locale.ROOT, "%04d%02d%02d%02d", calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.HOUR_OF_DAY),
        )
    }

    private fun zeroTime(calendar: Calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }

    private fun calendar(year: Int, month: Int, day: Int): Calendar = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, day, 0, 0, 0)
    }

    private fun shiftMonth(date: JalaliDate, delta: Int): JalaliDate {
        val monthIndex = date.year * 12 + date.month - 1 + delta
        val year = Math.floorDiv(monthIndex, 12)
        val month = Math.floorMod(monthIndex, 12) + 1
        return JalaliDate(year, month, minOf(date.day, monthLength(year, month)))
    }

    private fun monthLength(year: Int, month: Int): Int {
        if (month <= 6) return 31
        if (month <= 11) return 30
        val start = toGregorian(year, 1, 1)
        val next = toGregorian(year + 1, 1, 1)
        return if (dayNumber(next[0], next[1], next[2]) - dayNumber(start[0], start[1], start[2]) == 366) 30 else 29
    }

    private fun fromGregorian(gy: Int, gm: Int, gd: Int): JalaliDate {
        var jy = gy - 621
        var start = toGregorian(jy, 1, 1)
        val currentDay = dayNumber(gy, gm, gd)
        if (currentDay < dayNumber(start[0], start[1], start[2])) {
            jy--
            start = toGregorian(jy, 1, 1)
        }
        val dayOfYear = currentDay - dayNumber(start[0], start[1], start[2])
        if (dayOfYear < 186) return JalaliDate(jy, dayOfYear / 31 + 1, dayOfYear % 31 + 1)
        val remaining = dayOfYear - 186
        return JalaliDate(jy, remaining / 30 + 7, remaining % 30 + 1)
    }

    private fun toGregorian(jy: Int, jm: Int, jd: Int): IntArray {
        val gy = jy + 621
        val start = calendar(gy, 3, jalaliMarch(jy))
        val offset = if (jm <= 6) (jm - 1) * 31 + jd - 1 else 186 + (jm - 7) * 30 + jd - 1
        start.add(Calendar.DAY_OF_YEAR, offset)
        return intArrayOf(start.get(Calendar.YEAR), start.get(Calendar.MONTH) + 1, start.get(Calendar.DAY_OF_MONTH))
    }

    private fun jalaliMarch(jy: Int): Int {
        if (jy < JALALI_BREAKS[0] || jy >= JALALI_BREAKS[JALALI_BREAKS.size - 1]) return 20
        val gy = jy + 621
        var leapJ = -14
        var jp = JALALI_BREAKS[0]
        var jump = 0
        for (i in 1 until JALALI_BREAKS.size) {
            val jm = JALALI_BREAKS[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += (jump / 33) * 8 + (jump % 33) / 4
            jp = jm
        }
        val n = jy - jp
        leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4
        if (jump % 33 == 4 && jump - n == 4) leapJ++
        val leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150
        return 20 + leapJ - leapG
    }

    private fun dayNumber(year: Int, month: Int, day: Int): Int {
        val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT).apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
        }
        return (calendar.timeInMillis / 86_400_000L).toInt()
    }

    companion object {
        const val DAY = 0
        const val WEEK = 1
        const val MONTH = 2
        const val YEAR = 3

        private const val KEY = "hourly_totals"
        private const val TIME = "millis"
        private const val BOOKS = "byBook"
        private const val PERSIST_INTERVAL_MS = 5_000L
        private val JALALI_BREAKS = intArrayOf(
            -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181,
            1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
        )
        @JvmField val JALALI_MONTHS = arrayOf(
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
        )
    }
}
