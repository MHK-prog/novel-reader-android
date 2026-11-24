package com.novelreader.app

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.LinkedHashSet
import java.util.Locale

/** Canonical genre names, aliases, and the one-time library normalization rules. */
object GenreCatalog {
    @JvmStatic
    fun appendUnique(library: JSONObject?, genres: JSONArray, value: String?) {
        appendUniqueRaw(genres, canonicalName(library, value))
    }

    @JvmStatic
    fun appendUniqueRaw(genres: JSONArray, value: String?) {
        val candidate = value?.trim().orEmpty()
        if (candidate.isEmpty() || candidate.equals("Other", ignoreCase = true)) return
        for (i in 0 until genres.length()) {
            if (candidate.equals(genres.optString(i).trim(), ignoreCase = true)) return
        }
        genres.put(candidate)
    }

    @JvmStatic
    fun equivalentGroups(library: JSONObject?): JSONArray =
        library?.optJSONArray("genreEquivalents") ?: JSONArray()

    @JvmStatic
    fun canonicalName(library: JSONObject?, value: String?): String {
        var candidate = value?.trim().orEmpty()
        if (candidate.isEmpty()) return ""
        val groups = equivalentGroups(library)
        val visited = HashSet<String>()
        for (pass in 0..groups.length()) {
            if (!visited.add(candidate.lowercase(Locale.ROOT))) break
            var primary: String? = null
            for (i in 0 until groups.length()) {
                val group = groups.optJSONObject(i) ?: continue
                val aliases = group.optJSONArray("aliases") ?: continue
                for (j in 0 until aliases.length()) {
                    if (candidate.equals(aliases.optString(j).trim(), ignoreCase = true)) {
                        primary = group.optString("primary", "").trim()
                        break
                    }
                }
                if (primary != null) break
            }
            if (primary.isNullOrEmpty()) break
            candidate = primary
        }
        return findName(library?.optJSONArray("genres"), candidate)
            ?: TextNormalization.titleCaseWords(candidate)
    }

    @JvmStatic
    fun canonicalizePreferences(
        library: JSONObject?,
        favoriteGenres: MutableSet<String>,
        dislikedGenres: MutableSet<String>,
        activeGenreFilter: MutableSet<String>
    ) {
        val favorites = LinkedHashSet<String>()
        favoriteGenres.forEach {
            val canonical = canonicalName(library, it)
            if (canonical.isNotEmpty()) favorites.add(canonical)
        }
        favoriteGenres.clear()
        favoriteGenres.addAll(favorites)

        val dislikes = LinkedHashSet<String>()
        dislikedGenres.forEach {
            val canonical = canonicalName(library, it)
            if (canonical.isNotEmpty() && !containsIgnoreCase(favorites, canonical)) dislikes.add(canonical)
        }
        dislikedGenres.clear()
        dislikedGenres.addAll(dislikes)

        val filters = LinkedHashSet<String>()
        activeGenreFilter.forEach {
            val canonical = canonicalName(library, it)
            if (canonical.isNotEmpty()) filters.add(canonical)
        }
        activeGenreFilter.clear()
        activeGenreFilter.addAll(filters)
    }

    @JvmStatic
    fun canonicalizeLibrary(
        library: JSONObject,
        favoriteGenres: MutableSet<String>,
        dislikedGenres: MutableSet<String>,
        activeGenreFilter: MutableSet<String>
    ) {
        val genres = JSONArray()
        library.optJSONArray("genres")?.let { source ->
            for (i in 0 until source.length()) addUniqueGenre(library, genres, source.optString(i))
        }
        library.optJSONArray("books")?.let { books ->
            for (i in 0 until books.length()) {
                val book = books.optJSONObject(i) ?: continue
                val bookGenres = JSONArray()
                book.optJSONArray("genres")?.let { source ->
                    for (j in 0 until source.length()) {
                        addUniqueGenre(library, bookGenres, source.optString(j))
                    }
                }
                put(book, "genres", bookGenres)
                for (j in 0 until bookGenres.length()) addUniqueGenre(library, genres, bookGenres.optString(j))
            }
        }
        put(library, "genres", genres)
        canonicalizePreferences(library, favoriteGenres, dislikedGenres, activeGenreFilter)
    }

    @JvmStatic
    fun allCandidates(library: JSONObject?, knownGenres: Collection<String>): List<String> {
        val values = LinkedHashSet<String>()
        values.addAll(knownGenres)
        val groups = equivalentGroups(library)
        for (i in 0 until groups.length()) {
            val aliases = groups.optJSONObject(i)?.optJSONArray("aliases") ?: continue
            for (j in 0 until aliases.length()) values.add(aliases.optString(j))
        }
        return ArrayList(values)
    }

    @JvmStatic
    fun renamedTags(library: JSONObject?, source: JSONArray?, oldName: String, newName: String): JSONArray {
        val result = JSONArray()
        if (source == null) return result
        for (i in 0 until source.length()) {
            val value = source.optString(i)
            addUniqueGenre(library, result, if (value.equals(oldName, ignoreCase = true)) newName else value)
        }
        return result
    }

    @JvmStatic
    fun replaceSetValue(values: MutableSet<String>, oldValue: String, newValue: String) {
        var found = false
        values.toList().forEach { value ->
            if (value.equals(oldValue, ignoreCase = true)) {
                values.remove(value)
                found = true
            }
        }
        if (found) values.add(newValue)
    }

    @JvmStatic
    fun addName(genres: MutableCollection<String>, value: String?) {
        val candidate = value?.trim().orEmpty()
        if (candidate.isEmpty() || findName(genres, candidate) != null) return
        genres.add(candidate)
    }

    @JvmStatic
    fun findName(genres: Collection<String>, value: String?): String? {
        val candidate = value ?: return null
        return genres.firstOrNull { it.equals(candidate, ignoreCase = true) }
    }

    @JvmStatic
    fun findName(genres: JSONArray?, value: String?): String? {
        val candidate = value ?: return null
        if (genres == null) return null
        for (i in 0 until genres.length()) {
            val genre = genres.optString(i)
            if (genre.equals(candidate, ignoreCase = true)) return genre
        }
        return null
    }

    private fun addUniqueGenre(library: JSONObject?, destination: JSONArray, value: String?) {
        val candidate = canonicalName(library, value)
        if (candidate.isEmpty() || candidate.equals("Other", ignoreCase = true)) return
        for (i in 0 until destination.length()) {
            if (candidate.equals(destination.optString(i).trim(), ignoreCase = true)) return
        }
        destination.put(candidate)
    }

    private fun containsIgnoreCase(values: Collection<String>, candidate: String): Boolean =
        values.any { it.equals(candidate, ignoreCase = true) }

    private fun put(target: JSONObject, key: String, value: Any) {
        try {
            target.put(key, value)
        } catch (_: JSONException) {
        }
    }
}
