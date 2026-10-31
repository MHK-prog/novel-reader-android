package com.novelreader.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Small weighted-by-genre recommendation helper; Android-free and dependency-free. */
final class BookRecommendationEngine {
    private static final int DEFAULT_WEIGHT = 10;
    private static final double EXPLORATION_RATE = 0.20;

    static final class Book {
        final long id;
        final String title;
        final String genre;
        final String author;

        Book(long id, String title, String genre, String author) {
            this.id = id;
            this.title = title == null ? "" : title;
            this.genre = normalizeGenre(genre);
            this.author = author == null ? "" : author;
        }
    }

    static final class UserPreferences {
        final Map<String, Integer> genreWeights = new HashMap<>();

        int weightFor(String genre) {
            String key = normalizeGenre(genre).toLowerCase(Locale.ROOT);
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
        Map<String, List<Book>> byGenre = new LinkedHashMap<>();
        Map<Long, Book> unseenBooks = new LinkedHashMap<>();
        for (Book book : books) {
            if (book == null || book.genre.isEmpty() || "Other".equalsIgnoreCase(book.genre)) continue;
            if (seenBookIds != null && seenBookIds.contains(book.id)) continue;
            List<Book> genreBooks = byGenre.get(book.genre);
            if (genreBooks == null) {
                genreBooks = new ArrayList<>();
                byGenre.put(book.genre, genreBooks);
            }
            genreBooks.add(book);
            if (!unseenBooks.containsKey(book.id)) unseenBooks.put(book.id, book);
            preferences.weightFor(book.genre);
        }
        if (unseenBooks.isEmpty()) return null;

        if (random.nextDouble() < EXPLORATION_RATE) {
            List<Book> candidates = new ArrayList<>(unseenBooks.values());
            return candidates.get(random.nextInt(candidates.size()));
        }

        double totalWeight = 0;
        for (String genre : byGenre.keySet()) totalWeight += preferences.weightFor(genre);
        double selection = random.nextDouble() * totalWeight;
        String selectedGenre = null;
        for (String genre : byGenre.keySet()) {
            selection -= preferences.weightFor(genre);
            if (selection < 0) {
                selectedGenre = genre;
                break;
            }
        }
        if (selectedGenre == null) selectedGenre = byGenre.keySet().iterator().next();

        List<Book> candidates = byGenre.get(selectedGenre);
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    private static String normalizeGenre(String genre) {
        return genre == null ? "" : genre.trim();
    }
}
