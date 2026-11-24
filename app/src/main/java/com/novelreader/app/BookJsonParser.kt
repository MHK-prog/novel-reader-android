package com.novelreader.app

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Parses the supported book-import JSON shapes without depending on UI code. */
object BookJsonParser {
    @JvmStatic
    @Throws(JSONException::class)
    fun parseText(value: String?): BookJsonData {
        var source = value?.trim().orEmpty()
        if (source.startsWith("```") && source.endsWith("```")) {
            val firstLine = source.indexOf('\n')
            if (firstLine >= 0) source = source.substring(firstLine + 1, source.length - 3).trim()
        }
        if (source.isEmpty()) throw JSONException("Empty JSON")
        val root = JSONObject(source)
        return parse(root.optJSONObject("book") ?: root)
    }

    @JvmStatic
    fun parse(json: JSONObject): BookJsonData {
        val result = BookJsonData()
        result.hasTitle = json.has("title") || json.has("name")
        result.title = json.optString("title", json.optString("name", "")).trim()
        result.hasAuthor = json.has("author")
        result.author = json.optString("author", "").trim()

        val rawCover = when {
            json.has("coverUrl") -> json.opt("coverUrl")
            json.has("cover_url") -> json.opt("cover_url")
            else -> json.opt("cover")
        }
        if (rawCover is String) {
            result.coverUrl = rawCover.trim()
            result.hasCoverUrl = result.coverUrl.startsWith("https://")
        }

        result.hasDescription = json.has("description")
        result.description = json.optString("description", "").trim()
        result.hasCollection = json.has("collection") || json.has("series")
        result.collection = json.optString("collection", json.optString("series", "")).trim()
        result.hasSeriesNumber = json.has("seriesNumber") || json.has("bookNumber")
        result.seriesNumber = json.optInt("seriesNumber", json.optInt("bookNumber", 0))

        val rawCount = if (json.has("chapterCount")) json.opt("chapterCount") else json.opt("chaptersCount")
        result.hasChapterCount = json.has("chapterCount") || json.has("chaptersCount")
        result.chapterCount = when (rawCount) {
            is Number -> rawCount.toInt()
            is String -> rawCount.trim().toIntOrNull() ?: 0
            else -> 0
        }

        val genreKey = if (json.has("genres")) "genres" else "tags"
        result.hasGenres = json.has(genreKey)
        when (val rawGenres = json.opt(genreKey)) {
            is JSONArray -> {
                for (index in 0 until rawGenres.length()) {
                    rawGenres.optString(index, "").trim().takeIf { it.isNotEmpty() }?.let(result.genres::add)
                }
            }
            is String -> rawGenres.split(',', '\u060C').map { it.trim() }
                .filter { it.isNotEmpty() }.forEach { result.genres.add(it) }
        }
        return result
    }
}
