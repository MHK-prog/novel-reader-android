package com.novelreader.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.Locale

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
        readNames("favorite_genres", state.favoriteGenres)
        readNames("disliked_genres", state.dislikedGenres)
        readNames("favorite_authors", state.favoriteAuthors)
        readNames("disliked_authors", state.dislikedAuthors)
    }

    fun rebuildWeights(knownGenres: JSONArray?) {
        val names = LinkedHashMap<String, String>()
        if (knownGenres != null) {
            for (i in 0 until knownGenres.length()) rememberGenre(names, knownGenres.optString(i))
        }
        val preferred = HashSet<String>()
        val disliked = HashSet<String>()
        state.favoriteGenres.forEach { rememberGenre(names, it)?.let(preferred::add) }
        state.dislikedGenres.forEach { rememberGenre(names, it)?.let(disliked::add) }

        model.genreWeights.clear()
        names.keys.forEach { model.genreWeights[it] = 10 }
        preferred.forEach { if (names.containsKey(it)) model.genreWeights[it] = 30 }
        disliked.forEach {
            if (it !in preferred && names.containsKey(it)) model.genreWeights[it] = 1
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
            .putStringSet("favorite_genres", HashSet(state.favoriteGenres))
            .putStringSet("disliked_genres", HashSet(state.dislikedGenres))
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
}
