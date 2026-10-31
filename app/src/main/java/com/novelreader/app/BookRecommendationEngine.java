package com.novelreader.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Small weighted-by-genre recommendation helper; Android-free and dependency-free. */
final class BookRecommendationEngine {
    private static final int DEFAULT_WEIGHT = 10;

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
            String key = normalizeGenre(genre);
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
        for (Book book : books) {
            if (book == null) continue;
            byGenre.computeIfAbsent(book.genre, ignored -> new ArrayList<>()).add(book);
            preferences.weightFor(book.genre);
        }
        if (byGenre.isEmpty()) return null;

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

        List<Book> candidates = unseen(byGenre.get(selectedGenre), seenBookIds);
        if (candidates.isEmpty()) {
            candidates = new ArrayList<>();
            for (List<Book> genreBooks : byGenre.values()) {
                for (Book book : genreBooks) {
                    if (seenBookIds == null || !seenBookIds.contains(book.id)) candidates.add(book);
                }
            }
        }
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    void recordFeedback(String genre, boolean accepted) {
        String key = normalizeGenre(genre);
        int current = preferences.weightFor(key);
        long changed = (long) current + (accepted ? 4 : -2);
        preferences.genreWeights.put(key, (int) Math.max(1, Math.min(Integer.MAX_VALUE, changed)));
    }

    private List<Book> unseen(List<Book> books, Set<Long> seenBookIds) {
        List<Book> result = new ArrayList<>();
        if (books == null) return result;
        for (Book book : books) {
            if (seenBookIds == null || !seenBookIds.contains(book.id)) result.add(book);
        }
        return result;
    }

    private static String normalizeGenre(String genre) {
        String value = genre == null ? "" : genre.trim();
        return value.isEmpty() ? "Other" : value;
    }
}
