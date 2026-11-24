package com.novelreader.app

import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.Locale
import java.util.Random

/** Small weighted book picker. Ratings affect only the matching book. */
class BookRecommendationEngine(private val preferences: UserPreferences) {
    class Book(
        id: Long,
        title: String?,
        genres: List<String>?,
        author: String?,
        favoriteAuthor: Boolean,
        dislikedAuthor: Boolean,
        rating: Int,
    ) {
        @JvmField val id: Long = id
        @JvmField val title: String = title ?: ""
        @JvmField val genres: List<String> = if (genres == null) ArrayList() else ArrayList(genres)
        @JvmField val author: String = author ?: ""
        @JvmField val favoriteAuthor: Boolean = favoriteAuthor
        @JvmField val dislikedAuthor: Boolean = dislikedAuthor
        @JvmField val rating: Int = rating.coerceIn(0, 5)
    }

    class WeightBreakdown(genre: Double, author: Double, rating: Double) {
        @JvmField val genre: Double = genre
        @JvmField val author: Double = author
        @JvmField val rating: Double = rating
        @JvmField val total: Double = maxOf(0.05, genre * author * rating)
    }

    class UserPreferences {
        @JvmField val genreWeights: MutableMap<String, Int> = HashMap()

        fun weightFor(genre: String?): Int {
            val key = normalize(genre)
            val weight = genreWeights[key] ?: return DEFAULT_WEIGHT.also {
                genreWeights[key] = it
            }
            val safeWeight = maxOf(1, weight)
            genreWeights[key] = safeWeight
            return safeWeight
        }
    }

    private val random = Random()

    fun recommendBook(books: List<Book>?, seenBookIds: Set<Long>?): Book? =
        recommendBook(books, seenBookIds, "")

    fun recommendBook(
        books: List<Book>?,
        seenBookIds: Set<Long>?,
        temporaryGenreBoost: String?,
    ): Book? {
        if (books.isNullOrEmpty()) return null
        val candidates = ArrayList<Book>()
        val ids = HashSet<Long>()
        for (book in books) {
            if (!ids.add(book.id)) continue
            if (seenBookIds == null || !seenBookIds.contains(book.id)) candidates.add(book)
        }
        if (candidates.isEmpty()) {
            candidates.addAll(books)
            if (candidates.isEmpty()) return null
        }
        if (candidates.size == 1 || random.nextDouble() < EXPLORATION_RATE) {
            return candidates[random.nextInt(candidates.size)]
        }

        var total = 0.0
        for (book in candidates) total += bookWeight(book, temporaryGenreBoost)
        if (total <= 0.0) return candidates[random.nextInt(candidates.size)]

        var selection = random.nextDouble() * total
        for (book in candidates) {
            selection -= bookWeight(book, temporaryGenreBoost)
            if (selection < 0.0) return book
        }
        return candidates.last()
    }

    fun bookWeight(book: Book): Double = weightBreakdown(book).total

    private fun bookWeight(book: Book, boostedGenre: String?): Double {
        val parts = weightBreakdown(book)
        if (boostedGenre.isNullOrEmpty()) return parts.total
        if (book.genres.any { normalize(it) == normalize(boostedGenre) }) {
            return maxOf(0.05, (parts.genre + 25) * parts.author * parts.rating)
        }
        return parts.total
    }

    fun weightBreakdown(book: Book): WeightBreakdown {
        val uniqueGenres = HashSet<String>()
        var totalGenreWeight = 0.0
        for (genre in book.genres) {
            val key = normalize(genre)
            if (key.isEmpty() || key == "other" || !uniqueGenres.add(key)) continue
            totalGenreWeight += preferences.weightFor(key)
        }
        val genreWeight = if (uniqueGenres.isEmpty()) DEFAULT_WEIGHT.toDouble()
        else totalGenreWeight / uniqueGenres.size
        val authorWeight = when {
            book.favoriteAuthor -> 1.15
            book.dislikedAuthor -> 0.35
            else -> 1.0
        }
        // An unset rating is neutral. A manual score changes only this book's multiplier.
        val ratingWeight = if (book.rating == 0) 1.0 else 0.65 + book.rating * 0.15
        return WeightBreakdown(genreWeight, authorWeight, ratingWeight)
    }

    companion object {
        private const val DEFAULT_WEIGHT = 10
        private const val EXPLORATION_RATE = 0.15

        private fun normalize(value: String?): String = value?.trim()?.lowercase(Locale.ROOT) ?: ""
    }
}
