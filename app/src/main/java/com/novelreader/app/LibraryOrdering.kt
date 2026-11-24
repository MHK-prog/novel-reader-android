package com.novelreader.app

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal

/** Shared ordering rules for books, collections, authors, genres, and Chapters. */
object LibraryOrdering {
    private val chapterNumberPattern =
        Regex("^(?:chapter|ch)?\\s*([0-9]+(?:\\.[0-9]+)?)(?:\\b.*)?$", RegexOption.IGNORE_CASE)

    @JvmStatic
    fun ensureOrderIndexes(items: JSONArray?, chapterNames: Boolean) {
        if (items == null) return
        val ordered = ArrayList<JSONObject>()
        val used = HashSet<Int>()
        var hasValidIndexes = true
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            ordered.add(item)
            val index = item.optInt("orderIndex", Int.MIN_VALUE)
            if (!item.has("orderIndex") || index < 0 || !used.add(index)) hasValidIndexes = false
        }

        ordered.sortWith { first, second ->
            if (hasValidIndexes) {
                val savedOrder = first.optInt("orderIndex").compareTo(second.optInt("orderIndex"))
                if (savedOrder != 0) return@sortWith savedOrder
            }
            val firstTitle = first.optString("title", "")
            val secondTitle = second.optString("title", "")
            if (chapterNames) {
                val firstNumber = chapterNumber(firstTitle)
                val secondNumber = chapterNumber(secondTitle)
                if (firstNumber != null && secondNumber != null) {
                    val numberOrder = firstNumber.compareTo(secondNumber)
                    if (numberOrder != 0) return@sortWith numberOrder
                }
            }
            firstTitle.compareTo(secondTitle, ignoreCase = true)
        }

        ordered.forEachIndexed { index, item ->
            try {
                item.put("orderIndex", index)
                items.put(index, item)
            } catch (_: JSONException) {
                // Keep the remaining rows available if one malformed JSON item cannot be updated.
            }
        }
    }

    @JvmStatic
    fun chapterNumber(label: String?): BigDecimal? {
        val match = label?.trim()?.let(chapterNumberPattern::matchEntire) ?: return null
        return match.groupValues[1].toBigDecimalOrNull()
    }

    @JvmStatic
    fun nextOrderIndex(items: JSONArray?): Int {
        var next = 0
        if (items != null) {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                next = maxOf(next, item.optInt("orderIndex", -1) + 1)
            }
        }
        return next
    }

    /** Moves an item between two visible positions and writes the resulting indexes. */
    @JvmStatic
    fun moveJsonItem(items: JSONArray?, key: String, sourceId: String, targetId: String): Boolean {
        if (items == null) return false
        val ordered = ArrayList<JSONObject>()
        for (i in 0 until items.length()) items.optJSONObject(i)?.let(ordered::add)
        var from = -1
        var to = -1
        ordered.forEachIndexed { index, item ->
            val id = if (key == "storageIndex") {
                ChapterCatalog.storageIndex(item, index).toString()
            } else item.optString(key)
            if (sourceId == id) from = index
            if (targetId == id) to = index
        }
        if (from < 0 || to < 0 || from == to) return false

        val moving = ordered.removeAt(from)
        ordered.add(to, moving)
        ordered.forEachIndexed { index, item ->
            put(item, "orderIndex", index)
            try {
                items.put(index, item)
            } catch (_: JSONException) {
            }
        }
        return true
    }

    /** Reorders only the visible books while preserving the slots of filtered-out books. */
    @JvmStatic
    fun reorderVisibleBooks(
        library: JSONObject,
        visibleBooks: List<JSONObject>,
        sourceId: String,
        targetId: String
    ): Boolean {
        val visible = ArrayList(visibleBooks)
        val from = visible.indexOfFirst { it.optString("id") == sourceId }
        val to = visible.indexOfFirst { it.optString("id") == targetId }
        if (from < 0 || to < 0 || from == to) return false
        visible.add(to, visible.removeAt(from))

        val visibleIds = visible.mapTo(HashSet()) { it.optString("id") }
        val allBooks = library.optJSONArray("books") ?: return false
        var nextVisible = 0
        for (i in 0 until allBooks.length()) {
            val item = allBooks.optJSONObject(i) ?: continue
            if (visibleIds.contains(item.optString("id"))) {
                try {
                    allBooks.put(i, visible[nextVisible++])
                } catch (_: JSONException) {
                }
            }
        }
        val seriesPositions = HashMap<String, Int>()
        for (i in 0 until allBooks.length()) {
            val item = allBooks.optJSONObject(i) ?: continue
            put(item, "orderIndex", i)
            val collectionId = item.optString("collectionId", "other")
            if (collectionId == "other") continue
            val number = seriesPositions[collectionId] ?: 1
            put(item, "seriesNumber", number)
            seriesPositions[collectionId] = number + 1
        }
        return true
    }

    @JvmStatic
    fun ensureNamedOrderIndexes(items: JSONArray?, nameKey: String) {
        if (items == null) return
        val ordered = ArrayList<JSONObject>()
        for (i in 0 until items.length()) items.optJSONObject(i)?.let(ordered::add)
        val used = HashSet<Int>()
        val indexesValid = ordered.all {
            val index = it.optInt("orderIndex", -1)
            index >= 0 && used.add(index)
        }
        if (!indexesValid) {
            ordered.sortWith { first, second ->
                first.optString(nameKey).compareTo(second.optString(nameKey), ignoreCase = true)
            }
        } else {
            ordered.sortBy { it.optInt("orderIndex") }
        }
        ordered.forEachIndexed { index, item ->
            put(item, "orderIndex", index)
            try {
                items.put(index, item)
            } catch (_: JSONException) {
            }
        }
    }

    private fun put(item: JSONObject, key: String, value: Any) {
        try {
            item.put(key, value)
        } catch (_: JSONException) {
        }
    }
}
