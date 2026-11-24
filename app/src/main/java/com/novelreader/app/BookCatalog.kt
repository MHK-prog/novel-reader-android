package com.novelreader.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Filters and sorts books for the shared library screen. */
object BookCatalog {
    @JvmStatic
    fun visibleBooks(
        library: JSONObject,
        query: String,
        allBooks: Boolean,
        collectionId: String,
        selectedGenres: Set<String>,
        sortMode: String
    ): ArrayList<JSONObject> {
        val books = library.optJSONArray("books") ?: return arrayListOf()
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        val result = ArrayList<JSONObject>()
        for (i in 0 until books.length()) {
            val book = books.optJSONObject(i) ?: continue
            if (!allBooks && collectionId != book.optString("collectionId", "other")) continue
            if (!matchesQuery(library, book, normalizedQuery)) continue
            if (!containsSelectedGenres(book.optJSONArray("genres"), selectedGenres)) continue
            result.add(book)
        }

        if (!allBooks && collectionId != "other") {
            result.sortBy { LibraryMetrics.seriesBookNumber(it) }
        } else when (sortMode) {
            "title" -> result.sortWith { first, second ->
                first.optString("title").compareTo(second.optString("title"), ignoreCase = true)
            }
            "manual" -> result.sortBy { it.optInt("orderIndex", 0) }
            else -> result.sortWith { first, second ->
                val recent = second.optLong("lastReadAt", 0).compareTo(first.optLong("lastReadAt", 0))
                if (recent != 0) recent else first.optInt("orderIndex", 0).compareTo(second.optInt("orderIndex", 0))
            }
        }
        return result
    }

    private fun matchesQuery(library: JSONObject, book: JSONObject, query: String): Boolean {
        if (query.isEmpty()) return true
        if (book.optString("title").lowercase(Locale.ROOT).contains(query) ||
            book.optString("author").lowercase(Locale.ROOT).contains(query)
        ) return true

        val genres = book.optJSONArray("genres")
        if (genres != null) {
            for (i in 0 until genres.length()) {
                if (genres.optString(i).lowercase(Locale.ROOT).contains(query)) return true
            }
        }
        val collectionId = book.optString("collectionId", "other")
        val collections = library.optJSONArray("collections") ?: return false
        for (i in 0 until collections.length()) {
            val collection = collections.optJSONObject(i) ?: continue
            if (collection.optString("id") == collectionId) {
                return collection.optString("title").lowercase(Locale.ROOT).contains(query)
            }
        }
        return false
    }

    private fun containsSelectedGenres(genres: JSONArray?, selected: Set<String>): Boolean {
        if (selected.isEmpty()) return true
        if (genres == null) return false
        for (required in selected) {
            var found = false
            for (i in 0 until genres.length()) {
                if (required == genres.optString(i)) {
                    found = true
                    break
                }
            }
            if (!found) return false
        }
        return true
    }
}
