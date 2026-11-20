package com.novelreader.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

/** Stores the last opened chapter and its viewport position independently from library writes. */
final class ReadingPositionStore {
    private static final String PREFERENCES = "recent_reading";
    private final SharedPreferences preferences;

    ReadingPositionStore(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    String recentBookId() { return preferences.getString("book_id", ""); }
    String recentCollectionId() { return preferences.getString("collection_id", "other"); }
    int recentChapterStorageIndex() { return preferences.getInt("chapter_storage_index", 0); }

    void rememberChapter(String bookId, String collectionId, int chapterStorageIndex) {
        boolean sameChapter = bookId != null && bookId.equals(preferences.getString("book_id", ""))
                && chapterStorageIndex == preferences.getInt("chapter_storage_index", -1);
        SharedPreferences.Editor edit = preferences.edit()
                .putString("book_id", bookId == null ? "" : bookId)
                .putString("collection_id", collectionId == null ? "other" : collectionId)
                .putInt("chapter_storage_index", Math.max(0, chapterStorageIndex));
        if (!sameChapter) edit.putBoolean("has_scroll_position", false);
        edit.apply();
    }

    void savePosition(String bookId, String collectionId, int chapterStorageIndex,
                      JSONObject chapter, int scrollY, int viewportHeight, int scrollRange,
                      boolean commit) {
        int safeScroll = Math.max(0, scrollY);
        int safeRange = Math.max(0, scrollRange);
        int safeViewport = Math.max(0, viewportHeight);
        float fraction = safeRange == 0 ? 0f
                : Math.max(0f, Math.min(1f, safeScroll / (float) safeRange));

        if (chapter != null) {
            try {
                chapter.put("scroll", safeScroll);
                chapter.put("scrollRange", safeRange);
                chapter.put("scrollViewportHeight", safeViewport);
                chapter.put("scrollFraction", fraction);
            } catch (JSONException ignored) { }
        }

        SharedPreferences.Editor edit = preferences.edit()
                .putString("book_id", bookId == null ? "" : bookId)
                .putString("collection_id", collectionId == null ? "other" : collectionId)
                .putInt("chapter_storage_index", Math.max(0, chapterStorageIndex))
                .putInt("scroll_y", safeScroll)
                .putInt("scroll_range", safeRange)
                .putInt("scroll_viewport_height", safeViewport)
                .putFloat("scroll_fraction", fraction)
                .putBoolean("has_scroll_position", true);
        if (commit) edit.commit(); else edit.apply();
    }

    int restoreScroll(String bookId, int chapterStorageIndex, JSONObject chapter,
                      int viewportHeight, int maxScroll) {
        int safeMax = Math.max(0, maxScroll);
        if (matches(bookId, chapterStorageIndex) && preferences.getBoolean("has_scroll_position", false)) {
            int savedViewport = preferences.getInt("scroll_viewport_height", 0);
            int savedY = preferences.getInt("scroll_y", 0);
            int savedRange = preferences.getInt("scroll_range", 0);
            float savedFraction = preferences.getFloat("scroll_fraction", -1f);
            if (savedViewport > 0 && viewportHeight > 0 && savedViewport != viewportHeight
                    && savedFraction >= 0f) {
                return Math.round(safeMax * Math.max(0f, Math.min(1f, savedFraction)));
            }
            if (savedRange > 0 && safeMax > 0 && savedY > safeMax && savedFraction >= 0f) {
                return Math.round(safeMax * Math.max(0f, Math.min(1f, savedFraction)));
            }
            return Math.min(safeMax, Math.max(0, savedY));
        }

        if (chapter == null) return 0;
        int savedY = Math.max(0, chapter.optInt("scroll", 0));
        int savedViewport = chapter.optInt("scrollViewportHeight", 0);
        int savedRange = chapter.optInt("scrollRange", 0);
        float savedFraction = (float) chapter.optDouble("scrollFraction", -1d);
        if (savedFraction >= 0f && savedFraction <= 1f
                && ((savedViewport > 0 && viewportHeight > 0 && savedViewport != viewportHeight)
                || (savedRange > 0 && savedY > safeMax))) {
            return Math.round(safeMax * savedFraction);
        }
        if (savedY > 0) return Math.min(safeMax, savedY);
        int percent = Math.max(0, Math.min(100, chapter.optInt("percent", 0)));
        return Math.round(safeMax * percent / 100f);
    }

    private boolean matches(String bookId, int chapterStorageIndex) {
        return bookId != null && bookId.equals(preferences.getString("book_id", ""))
                && chapterStorageIndex == preferences.getInt("chapter_storage_index", -1);
    }
}
