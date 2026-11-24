package com.novelreader.app

import org.json.JSONArray
import org.json.JSONObject

/** Read-only library calculations shared by the book, chapter, and collection screens. */
object LibraryMetrics {
    @JvmStatic
    fun seriesBookNumber(book: JSONObject?): Int {
        if (book == null) return 0
        val number = book.optInt("seriesNumber", 0)
        return if (number > 0) number else maxOf(1, book.optInt("orderIndex", 0) + 1)
    }

    @JvmStatic
    fun nextSeriesBookNumber(collectionId: String, books: JSONArray?): Int {
        var next = 1
        if (books != null) for (i in 0 until books.length()) {
            val book = books.optJSONObject(i) ?: continue
            if (collectionId == book.optString("collectionId", "other")) {
                next = maxOf(next, seriesBookNumber(book) + 1)
            }
        }
        return next
    }

    @JvmStatic
    fun displayBookTitle(book: JSONObject?): String {
        if (book == null) return ""
        val title = book.optString("title", "بدون عنوان")
        return if (book.optString("collectionId", "other") != "other") {
            val number = seriesBookNumber(book)
            if (number > 0) "$title #$number" else title
        } else title
    }

    @JvmStatic
    fun bookReadPercent(book: JSONObject?): Int {
        val chapters = book?.optJSONArray("chapters") ?: return 0
        if (chapters.length() == 0) return 0
        var total = 0
        for (i in 0 until chapters.length()) {
            val chapter = chapters.optJSONObject(i) ?: continue
            val percent = when {
                !chapter.optBoolean("end", false) -> 0
                chapter.optBoolean("done") -> 100
                else -> chapter.optInt("percent", 0)
            }
            total += percent.coerceIn(0, 100)
        }
        return Math.round(total / chapters.length().toFloat())
    }

    @JvmStatic
    fun collectionReadPercent(collection: JSONObject?, books: JSONArray?): Int {
        if (collection == null || books == null) return 0
        val collectionId = collection.optString("id")
        var read = 0
        var total = 0
        for (i in 0 until books.length()) {
            val book = books.optJSONObject(i) ?: continue
            if (collectionId != book.optString("collectionId", "other")) continue
            val chapters = book.optJSONArray("chapters") ?: continue
            for (j in 0 until chapters.length()) {
                val chapter = chapters.optJSONObject(j) ?: continue
                total++
                if (chapter.optBoolean("done", false)) read++
            }
        }
        return if (total == 0) 0 else minOf(100, read * 100 / total)
    }

    @JvmStatic
    fun authorBookCount(author: String, books: JSONArray?): Int = countBooks(books) {
        author.equals(it.optString("author", "").trim(), ignoreCase = true)
    }

    @JvmStatic
    fun collectionBookCount(collectionId: String, books: JSONArray?): Int = countBooks(books) {
        collectionId == it.optString("collectionId", "other")
    }

    private inline fun countBooks(books: JSONArray?, matches: (JSONObject) -> Boolean): Int {
        if (books == null) return 0
        var count = 0
        for (i in 0 until books.length()) {
            val book = books.optJSONObject(i) ?: continue
            if (matches(book)) count++
        }
        return count
    }
}
