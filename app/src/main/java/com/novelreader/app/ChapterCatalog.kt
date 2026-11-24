package com.novelreader.app

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID
import kotlin.math.ceil

/** Chapter IDs, display labels, and numeric ordering helpers for a book's chapter list. */
object ChapterCatalog {
    @JvmStatic
    fun newChapter(): JSONObject = JSONObject().apply {
        try {
            put("id", UUID.randomUUID().toString())
            put("end", false)
            put("hasText", false)
            put("percent", 0)
            put("done", false)
            put("scroll", 0)
            put("manualDone", false)
            put("estimatedMinutes", 0)
        } catch (_: JSONException) {
        }
    }

    @JvmStatic
    fun ensureStorageIndexes(chapters: JSONArray?) {
        if (chapters == null) return
        for (i in 0 until chapters.length()) {
            val chapter = chapters.optJSONObject(i) ?: continue
            if (chapter.optInt("storageIndex", 0) < 1) {
                try {
                    chapter.put("storageIndex", i + 1)
                } catch (_: JSONException) {
                }
            }
        }
    }

    @JvmStatic
    fun insertionIndex(chapters: JSONArray, newLabel: String): Int {
        val number = LibraryOrdering.chapterNumber(newLabel) ?: return chapters.length()
        for (i in 0 until chapters.length()) {
            val chapter = chapters.optJSONObject(i)
            val existing = LibraryOrdering.chapterNumber(chapter?.optString("title", ""))
            if (existing != null && existing > number) return i
        }
        return chapters.length()
    }

    @JvmStatic
    fun largestWholeChapterNumber(book: JSONObject?): Int {
        val chapters = book?.optJSONArray("chapters") ?: return 0
        var largest = 0
        for (i in 0 until chapters.length()) {
            val chapter = chapters.optJSONObject(i)
            val number = LibraryOrdering.chapterNumber(chapter?.optString("title", "")) ?: continue
            if (number.stripTrailingZeros().scale() <= 0 &&
                number >= BigDecimal.ONE && number <= BigDecimal.valueOf(5000)
            ) {
                largest = maxOf(largest, number.toInt())
            }
        }
        return largest
    }

    @JvmStatic
    fun addMissingWholeChapters(book: JSONObject?, targetCount: Int) {
        var chapters = book?.optJSONArray("chapters") ?: return
        if (targetCount < 1) return
        ensureStorageIndexes(chapters)

        val present = HashSet<Int>()
        var nextStorageIndex = 1
        for (i in 0 until chapters.length()) {
            val chapter = chapters.optJSONObject(i) ?: continue
            nextStorageIndex = maxOf(nextStorageIndex, storageIndex(chapter, i) + 1)
            val number = LibraryOrdering.chapterNumber(chapter.optString("title")) ?: continue
            if (number.stripTrailingZeros().scale() <= 0 &&
                number >= BigDecimal.ONE && number <= BigDecimal.valueOf(5000)
            ) {
                present.add(number.toInt())
            }
        }

        for (number in 1..targetCount) {
            if (!present.add(number)) continue
            val chapter = newChapter()
            put(chapter, "title", "Chapter $number")
            put(chapter, "storageIndex", nextStorageIndex++)
            val insertion = insertionIndex(chapters, "Chapter $number")
            val reordered = JSONArray()
            for (i in 0..chapters.length()) {
                if (i == insertion) reordered.put(chapter)
                if (i < chapters.length()) reordered.put(chapters.opt(i))
            }
            chapters = reordered
        }

        for (i in 0 until chapters.length()) {
            chapters.optJSONObject(i)?.let { put(it, "orderIndex", i) }
        }
        book?.let { put(it, "chapters", chapters) }
    }

    @JvmStatic
    fun storageIndex(chapter: JSONObject?, position: Int): Int {
        val saved = chapter?.optInt("storageIndex", 0) ?: 0
        return if (saved > 0) saved else position + 1
    }

    @JvmStatic
    fun label(chapter: JSONObject?, index: Int): String {
        val saved = chapter?.optString("title", "")?.trim().orEmpty()
        return saved.ifEmpty { "Chapter ${index + 1}" }
    }

    @JvmStatic
    fun calculateAndStoreEstimate(chapter: JSONObject?, text: String?, wordsPerMinute: Int) {
        if (chapter == null) return
        val words = ChapterTextFormatter.countWords(text)
        val minutes = if (words <= 0) 0 else ceil(words / maxOf(1, wordsPerMinute).toDouble()).toInt()
        put(chapter, "estimatedMinutes", minutes)
    }

    @JvmStatic
    fun remainingTimeLabel(chapter: JSONObject?): String {
        if (chapter == null || !chapter.optBoolean("hasText") || !chapter.optBoolean("end")) return "0 min"
        val totalMinutes = maxOf(0, chapter.optInt("estimatedMinutes", 0))
        val percent = if (chapter.optBoolean("done")) 100
            else chapter.optInt("percent", 0).coerceIn(0, 100)
        val remaining = ceil(totalMinutes * (100 - percent) / 100.0).toLong()
        if (remaining <= 0) return "0 min"
        val hours = remaining / 60
        val minutes = remaining % 60
        if (hours == 0L) return "$remaining min"
        return if (minutes == 0L) "$hours h" else "$hours h $minutes min"
    }

    @JvmStatic
    fun firstUnread(book: JSONObject?): Int {
        val chapters = book?.optJSONArray("chapters") ?: return 0
        if (chapters.length() == 0) return 0
        for (i in 0 until chapters.length()) {
            val chapter = chapters.optJSONObject(i)
            if (chapter == null || !chapter.optBoolean("end", false) || !chapter.optBoolean("done")) {
                return i
            }
        }
        return chapters.length() - 1
    }

    private fun put(target: JSONObject, key: String, value: Any) {
        try {
            target.put(key, value)
        } catch (_: JSONException) {
        }
    }
}
