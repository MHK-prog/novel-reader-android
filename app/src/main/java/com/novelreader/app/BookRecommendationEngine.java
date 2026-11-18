package com.novelreader.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Small weighted book picker. Ratings affect only the matching book. */
final class BookRecommendationEngine {
    private static final int DEFAULT_WEIGHT = 10;
    private static final double EXPLORATION_RATE = 0.15;

    static final class Book {
        final long id;
        final String title;
        final List<String> genres;
        final String author;
        final boolean favoriteAuthor;
        final boolean dislikedAuthor;
        final int rating;

        Book(long id, String title, List<String> genres, String author,
             boolean favoriteAuthor, boolean dislikedAuthor, int rating) {
            this.id = id;
            this.title = title == null ? "" : title;
            this.genres = genres == null ? new ArrayList<>() : new ArrayList<>(genres);
            this.author = author == null ? "" : author;
            this.favoriteAuthor = favoriteAuthor;
            this.dislikedAuthor = dislikedAuthor;
            this.rating = Math.max(0, Math.min(5, rating));
        }
    }

    static final class WeightBreakdown {
        final double genre;
        final double author;
        final double rating;
        final double total;

        WeightBreakdown(double genre, double author, double rating) {
            this.genre = genre;
            this.author = author;
            this.rating = rating;
            this.total = Math.max(0.05, genre * author * rating);
        }
    }

    static final class UserPreferences {
        final Map<String, Integer> genreWeights = new HashMap<>();

        int weightFor(String genre) {
            String key = normalize(genre);
            Integer weight = genreWeights.get(key);
            if (weight == null) {
                genreWeights.put(key, DEFAULT_WEIGHT);
                return DEFAULT_WEIGHT;
            }
            int safeWeight = Math.max(1, weight);
            genreWeights.put(key, safeWeight);
            return safeWeight;
        }
    }

    private final UserPreferences preferences;
    private final Random random = new Random();

    BookRecommendationEngine(UserPreferences preferences) {
        this.preferences = preferences;
    }

    Book recommendBook(List<Book> books, Set<Long> seenBookIds) {
        return recommendBook(books, seenBookIds, "");
    }

    Book recommendBook(List<Book> books, Set<Long> seenBookIds, String temporaryGenreBoost) {
        if (books == null || books.isEmpty()) return null;
        List<Book> candidates = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (Book book : books) {
            if (book == null || !ids.add(book.id)) continue;
            if (seenBookIds == null || !seenBookIds.contains(book.id)) candidates.add(book);
        }
        if (candidates.isEmpty()) {
            for (Book book : books) if (book != null) candidates.add(book);
            if (candidates.isEmpty()) return null;
        }
        if (candidates.size() == 1 || random.nextDouble() < EXPLORATION_RATE) {
            return candidates.get(random.nextInt(candidates.size()));
        }
        double total = 0;
        for (Book book : candidates) total += bookWeight(book, temporaryGenreBoost);
        if (total <= 0) return candidates.get(random.nextInt(candidates.size()));
        double selection = random.nextDouble() * total;
        for (Book book : candidates) {
            selection -= bookWeight(book, temporaryGenreBoost);
            if (selection < 0) return book;
        }
        return candidates.get(candidates.size() - 1);
    }

    double bookWeight(Book book) {
        return weightBreakdown(book).total;
    }

    private double bookWeight(Book book, String boostedGenre) {
        WeightBreakdown parts = weightBreakdown(book);
        if (boostedGenre == null || boostedGenre.isEmpty()) return parts.total;
        for (String genre : book.genres) {
            if (normalize(genre).equals(normalize(boostedGenre))) {
                return Math.max(0.05, (parts.genre + 25) * parts.author * parts.rating);
            }
        }
        return parts.total;
    }

    WeightBreakdown weightBreakdown(Book book) {
        Set<String> uniqueGenres = new HashSet<>();
        double totalGenreWeight = 0;
        for (String genre : book.genres) {
            String key = normalize(genre);
            if (key.isEmpty() || "other".equals(key) || !uniqueGenres.add(key)) continue;
            totalGenreWeight += preferences.weightFor(key);
        }
        double genreWeight = uniqueGenres.isEmpty() ? DEFAULT_WEIGHT
                : totalGenreWeight / uniqueGenres.size();
        double authorWeight = book.favoriteAuthor ? 1.15 : book.dislikedAuthor ? 0.35 : 1.0;
        // An unset rating is neutral. A manual score changes only this book's multiplier.
        double ratingWeight = book.rating == 0 ? 1.0 : 0.65 + book.rating * 0.15;
        return new WeightBreakdown(genreWeight, authorWeight, ratingWeight);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
