package com.novelreader.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Small dependency-free recommendation helper. One candidate represents one book. */
final class BookRecommendationEngine {
    private static final int DEFAULT_WEIGHT = 10;
    private static final double EXPLORATION_RATE = 0.15;

    static final class Book {
        final long id;
        final String title;
        final List<String> genres;
        final String author;
        final int interestRating;
        final boolean favorite;
        final boolean favoriteAuthor;
        final long wordsRead;
        final long totalWordsRead;
        final boolean useReadingShare;

        Book(long id, String title, List<String> genres, String author,
             int interestRating, boolean favorite, boolean favoriteAuthor,
             long wordsRead, long totalWordsRead, boolean useReadingShare) {
            this.id = id;
            this.title = title == null ? "" : title;
            this.genres = genres == null ? new ArrayList<>() : new ArrayList<>(genres);
            this.author = author == null ? "" : author;
            this.interestRating = interestRating;
            this.favorite = favorite;
            this.favoriteAuthor = favoriteAuthor;
            this.wordsRead = Math.max(0, wordsRead);
            this.totalWordsRead = Math.max(0, totalWordsRead);
            this.useReadingShare = useReadingShare;
        }
    }

    static final class WeightBreakdown {
        final double genre;
        final double rating;
        final double personal;
        final double reading;
        final double total;

        WeightBreakdown(double genre, double rating, double personal, double reading) {
            this.genre = genre;
            this.rating = rating;
            this.personal = personal;
            this.reading = reading;
            this.total = Math.max(0.05, genre * rating * personal * reading);
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
        if (books == null || books.isEmpty()) return null;
        List<Book> candidates = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (Book book : books) {
            if (book == null || !ids.add(book.id)) continue;
            if (seenBookIds == null || !seenBookIds.contains(book.id)) candidates.add(book);
        }
        // Start a fresh recommendation cycle after every eligible book has appeared once.
        if (candidates.isEmpty()) {
            for (Book book : books) if (book != null) candidates.add(book);
            if (candidates.isEmpty()) return null;
        }
        if (candidates.size() == 1 || random.nextDouble() < EXPLORATION_RATE) {
            return candidates.get(random.nextInt(candidates.size()));
        }

        double total = 0;
        for (Book book : candidates) total += bookWeight(book);
        if (total <= 0) return candidates.get(random.nextInt(candidates.size()));
        double selection = random.nextDouble() * total;
        for (Book book : candidates) {
            selection -= bookWeight(book);
            if (selection < 0) return book;
        }
        return candidates.get(candidates.size() - 1);
    }

    double bookWeight(Book book) {
        return weightBreakdown(book).total;
    }

    WeightBreakdown weightBreakdown(Book book) {
        Set<String> uniqueGenres = new HashSet<>();
        double totalGenreWeight = 0;
        for (String genre : book.genres) {
            String key = normalize(genre);
            if (key.isEmpty() || "other".equals(key) || !uniqueGenres.add(key)) continue;
            totalGenreWeight += preferences.weightFor(key);
        }
        // Averaging prevents books with many tags from receiving extra lottery tickets.
        double genreWeight = uniqueGenres.isEmpty() ? DEFAULT_WEIGHT
                : totalGenreWeight / uniqueGenres.size();
        double ratingWeight = book.interestRating < 1 || book.interestRating > 5
                ? 1.0 : new double[]{0, 0.35, 0.60, 1.0, 1.50, 2.10}[book.interestRating];
        double personalWeight = (book.favorite ? 1.12 : 1.0)
                * (book.favoriteAuthor ? 1.12 : 1.0);
        double readingShareWeight = !book.useReadingShare || book.totalWordsRead == 0 ? 1.0
                : Math.max(0.05, Math.min(1.0, book.wordsRead / (double) book.totalWordsRead));
        return new WeightBreakdown(genreWeight, ratingWeight, personalWeight, readingShareWeight);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
