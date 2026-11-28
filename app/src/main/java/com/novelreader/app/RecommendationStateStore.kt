package com.novelreader.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.math.roundToInt

/** In-memory recommendation choices shared by the engine and the app's settings pages. */
class RecommendationUserState {
    @JvmField val seenRecommendationIds = HashSet<Long>()
    @JvmField val favoriteGenres = LinkedHashSet<String>()
    @JvmField val dislikedGenres = LinkedHashSet<String>()
    @JvmField val favoriteAuthors = LinkedHashSet<String>()
    @JvmField val dislikedAuthors = LinkedHashSet<String>()
}

/** Loads, normalizes, rebuilds, and persists the small recommendation profile. */
class RecommendationStateStore(
    private val preferences: SharedPreferences,
    private val state: RecommendationUserState,
    private val model: BookRecommendationEngine.UserPreferences
) {
    fun load() {
        model.genreWeights.clear()
        state.seenRecommendationIds.clear()
        state.favoriteGenres.clear()
        state.dislikedGenres.clear()
        state.favoriteAuthors.clear()
        state.dislikedAuthors.clear()

        try {
            val savedWeights = JSONObject(preferences.getString("genre_weights", "{}"))
            val names = savedWeights.keys()
            while (names.hasNext()) {
                val genre = names.next()
                model.genreWeights[genre.trim().lowercase(Locale.ROOT)] =
                    maxOf(1, savedWeights.optInt(genre, 10))
            }
        } catch (_: Exception) {
        }

        preferences.getStringSet("seen_books", emptySet()).orEmpty().forEach { value ->
            value.toLongOrNull()?.let(state.seenRecommendationIds::add)
        }
        readNames("favorite_authors", state.favoriteAuthors)
        readNames("disliked_authors", state.dislikedAuthors)
    }

    fun rebuildWeights(knownGenres: JSONArray?, books: JSONArray?) {
        val names = LinkedHashMap<String, String>()
        if (knownGenres != null) {
            for (i in 0 until knownGenres.length()) rememberGenre(names, knownGenres.optString(i))
        }
        val scores = HashMap<String, Double>()
        val counts = HashMap<String, Int>()
        if (books != null) for (i in 0 until books.length()) {
            val book = books.optJSONObject(i) ?: continue
            val rating = book.optInt("userRating", 0).coerceIn(0, 5)
            if (rating == 0) continue
            val genres = book.optJSONArray("genres") ?: continue
            val bookGenres = HashSet<String>()
            for (j in 0 until genres.length()) {
                val key = rememberGenre(names, genres.optString(j)) ?: continue
                if (bookGenres.add(key)) {
                    scores[key] = (scores[key] ?: 0.0) + (rating - 3).toDouble()
                    counts[key] = (counts[key] ?: 0) + 1
                }
            }
        }

        model.genreWeights.clear()
        names.keys.forEach { key ->
            val count = counts[key] ?: 0
            val average = if (count == 0) 0.0 else (scores[key] ?: 0.0) / count
            model.genreWeights[key] = (10 + (average * 4).roundToInt()).coerceIn(1, MAX_GENRE_WEIGHT)
        }
    }

    fun save() {
        val weights = JSONObject()
        model.genreWeights.forEach { (genre, value) ->
            try {
                weights.put(genre, maxOf(1, value))
            } catch (_: JSONException) {
            }
        }
        val seen = state.seenRecommendationIds.mapTo(HashSet()) { it.toString() }
        preferences.edit()
            .putString("genre_weights", weights.toString())
            .putStringSet("seen_books", seen)
            .remove("favorite_genres")
            .remove("disliked_genres")
            .remove("favorite_book_ids")
            .remove("disliked_book_ids")
            .putStringSet("favorite_authors", HashSet(state.favoriteAuthors))
            .putStringSet("disliked_authors", HashSet(state.dislikedAuthors))
            .apply()
    }

    private fun readNames(key: String, target: MutableSet<String>) {
        preferences.getStringSet(key, emptySet()).orEmpty().forEach { value ->
            val normalized = TextNormalization.titleCaseWords(value)
            if (normalized.isNotEmpty()) target.add(normalized)
        }
    }

    private fun rememberGenre(names: MutableMap<String, String>, value: String?): String? {
        val name = value?.trim().orEmpty()
        if (name.isEmpty() || name.equals("Other", ignoreCase = true)) return null
        val key = name.lowercase(Locale.ROOT)
        if (!names.containsKey(key)) names[key] = name
        return key
    }

    companion object {
        private const val MAX_GENRE_WEIGHT = 50
    }
}
