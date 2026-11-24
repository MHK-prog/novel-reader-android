package com.novelreader.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.roundToInt

/** Stores the last opened chapter and its viewport position independently from library writes. */
class ReadingPositionStore(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun recentBookId(): String = preferences.getString("book_id", "") ?: ""
    fun recentCollectionId(): String = preferences.getString("collection_id", "other") ?: "other"
    fun recentChapterStorageIndex(): Int = preferences.getInt("chapter_storage_index", 0)

    fun rememberChapter(bookId: String?, collectionId: String?, chapterStorageIndex: Int) {
        val sameChapter = bookId != null && bookId == preferences.getString("book_id", "") &&
            chapterStorageIndex == preferences.getInt("chapter_storage_index", -1)
        val edit = preferences.edit()
            .putString("book_id", bookId ?: "")
            .putString("collection_id", collectionId ?: "other")
            .putInt("chapter_storage_index", maxOf(0, chapterStorageIndex))
        if (!sameChapter) edit.putBoolean("has_scroll_position", false)
        edit.apply()
    }

    fun savePosition(
        bookId: String?,
        collectionId: String?,
        chapterStorageIndex: Int,
        chapter: JSONObject?,
        scrollY: Int,
        viewportHeight: Int,
        scrollRange: Int,
        commit: Boolean,
    ) {
        val safeScroll = maxOf(0, scrollY)
        val safeRange = maxOf(0, scrollRange)
        val safeViewport = maxOf(0, viewportHeight)
        val fraction = if (safeRange == 0) 0f else (safeScroll / safeRange.toFloat()).coerceIn(0f, 1f)

        if (chapter != null) {
            try {
                chapter.put("scroll", safeScroll)
                chapter.put("scrollRange", safeRange)
                chapter.put("scrollViewportHeight", safeViewport)
                chapter.put("scrollFraction", fraction)
            } catch (_: JSONException) {
            }
        }

        val edit = preferences.edit()
            .putString("book_id", bookId ?: "")
            .putString("collection_id", collectionId ?: "other")
            .putInt("chapter_storage_index", maxOf(0, chapterStorageIndex))
            .putInt("scroll_y", safeScroll)
            .putInt("scroll_range", safeRange)
            .putInt("scroll_viewport_height", safeViewport)
            .putFloat("scroll_fraction", fraction)
            .putBoolean("has_scroll_position", true)
        if (commit) edit.commit() else edit.apply()
    }

    fun restoreScroll(
        bookId: String?,
        chapterStorageIndex: Int,
        chapter: JSONObject?,
        viewportHeight: Int,
        maxScroll: Int,
    ): Int {
        val safeMax = maxOf(0, maxScroll)
        if (matches(bookId, chapterStorageIndex) && preferences.getBoolean("has_scroll_position", false)) {
            val savedViewport = preferences.getInt("scroll_viewport_height", 0)
            val savedY = preferences.getInt("scroll_y", 0)
            val savedRange = preferences.getInt("scroll_range", 0)
            val savedFraction = preferences.getFloat("scroll_fraction", -1f)
            if (savedViewport > 0 && viewportHeight > 0 && savedViewport != viewportHeight && savedFraction >= 0f) {
                return (safeMax * savedFraction.coerceIn(0f, 1f)).roundToInt()
            }
            if (savedRange > 0 && safeMax > 0 && savedY > safeMax && savedFraction >= 0f) {
                return (safeMax * savedFraction.coerceIn(0f, 1f)).roundToInt()
            }
            return savedY.coerceIn(0, safeMax)
        }

        if (chapter == null) return 0
        val savedY = maxOf(0, chapter.optInt("scroll", 0))
        val savedViewport = chapter.optInt("scrollViewportHeight", 0)
        val savedRange = chapter.optInt("scrollRange", 0)
        val savedFraction = chapter.optDouble("scrollFraction", -1.0).toFloat()
        if (savedFraction in 0f..1f &&
            ((savedViewport > 0 && viewportHeight > 0 && savedViewport != viewportHeight) ||
                (savedRange > 0 && savedY > safeMax))
        ) {
            return (safeMax * savedFraction).roundToInt()
        }
        if (savedY > 0) return minOf(safeMax, savedY)
        val percent = chapter.optInt("percent", 0).coerceIn(0, 100)
        return (safeMax * percent / 100f).roundToInt()
    }

    private fun matches(bookId: String?, chapterStorageIndex: Int): Boolean =
        bookId != null && bookId == preferences.getString("book_id", "") &&
            chapterStorageIndex == preferences.getInt("chapter_storage_index", -1)

    companion object {
        private const val PREFERENCES = "recent_reading"
    }
}
