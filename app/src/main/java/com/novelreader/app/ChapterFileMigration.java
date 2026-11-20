package com.novelreader.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Temporary one-time migration from flat Markdown files to ID-based plain-text files. */
final class ChapterFileMigration {
    private static final String PREFS = "chapter_file_layout";
    private static final String DONE = "ids_txt_migration_done";

    private ChapterFileMigration() { }

    static final class LegacyChapter {
        final String text;
        final boolean end;

        LegacyChapter(String text, boolean end) {
            this.text = text;
            this.end = end;
        }
    }

    static boolean isDone(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(DONE, false);
    }

    /** Remove this migration code after the 1.5.6 rollout has been completed. */
    static boolean migrateOnce(Context context, NovelStorage storage, JSONObject library) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (preferences.getBoolean(DONE, false)) return true;
        JSONArray books = library == null ? null : library.optJSONArray("books");
        if (books == null) return false;

        List<String> rootFiles = storage.listNames();
        try {
            for (int b = 0; b < books.length(); b++) {
                JSONObject book = books.optJSONObject(b);
                if (book == null) continue;
                String bookCode = book.optString("id", "");
                if (bookCode.isEmpty()) {
                    bookCode = UUID.randomUUID().toString();
                    book.put("id", bookCode);
                }
                JSONArray chapters = book.optJSONArray("chapters");
                if (chapters == null) continue;
                for (int c = 0; c < chapters.length(); c++) {
                    JSONObject chapter = chapters.optJSONObject(c);
                    if (chapter == null) continue;
                    String chapterCode = chapter.optString("id", "");
                    if (chapterCode.isEmpty()) {
                        chapterCode = UUID.randomUUID().toString();
                        chapter.put("id", chapterCode);
                    }
                    String targetName = chapterCode + ".txt";
                    List<String> candidates = legacyCandidates(bookCode, chapter, c, rootFiles);
                    if (storage.chapterFileExists(bookCode, targetName)) {
                        for (String oldName : candidates) {
                            if (!storage.deleteLegacyChapterFile(oldName)) return false;
                        }
                        continue;
                    }
                    String sourceName = bestCandidate(candidates);
                    String sourceText = sourceName == null ? null : storage.readLegacyChapterFile(sourceName);

                    boolean end = chapter.optBoolean("end", chapter.optBoolean("hasEndMarker", false));
                    if (sourceText != null) {
                        ChapterTextFormatter.CleanText cleaned = ChapterTextFormatter.cleanLegacyText(sourceText);
                        end |= cleaned.end;
                        if (!storage.writeChapterFile(bookCode, targetName, cleaned.text)) return false;
                        for (String oldName : candidates) {
                            if (!storage.deleteLegacyChapterFile(oldName)) return false;
                        }
                        chapter.put("hasText", !cleaned.text.trim().isEmpty());
                    } else if (!storage.chapterFileExists(bookCode, targetName)) {
                        chapter.put("hasText", false);
                    }
                    chapter.put("end", end);
                    chapter.remove("hasEndMarker");
                    if (!end) {
                        chapter.put("done", false);
                        chapter.put("percent", 0);
                        chapter.put("wordsRead", 0);
                    }
                }
            }
        } catch (Exception error) {
            android.util.Log.e("NovelReader", "Chapter text migration did not finish", error);
            return false;
        }

        if (!storage.migrateChapterOriginalsToTxt()) return false;
        return preferences.edit().putBoolean(DONE, true).commit();
    }

    static LegacyChapter migrateMissingChapter(NovelStorage storage, String bookId,
                                                JSONObject chapter, int position) throws Exception {
        List<String> names = storage.listNames();
        List<String> candidates = legacyCandidates(bookId, chapter, position, names);
        String sourceName = bestCandidate(candidates);
        if (sourceName == null) return null;
        String sourceText = storage.readLegacyChapterFile(sourceName);
        if (sourceText == null) return null;
        ChapterTextFormatter.CleanText cleaned = ChapterTextFormatter.cleanLegacyText(sourceText);
        String chapterId = chapter.optString("id", "");
        if (chapterId.isEmpty()) return null;
        if (!storage.writeChapterFile(bookId, chapterId + ".txt", cleaned.text)) {
            throw new java.io.IOException(storage.lastError());
        }
        for (String oldName : candidates) {
            if (!storage.deleteLegacyChapterFile(oldName)) {
                throw new java.io.IOException("Could not remove old Chapter file " + oldName + ".");
            }
        }
        return new LegacyChapter(cleaned.text, cleaned.end);
    }

    private static List<String> legacyCandidates(String bookId, JSONObject chapter,
                                                  int position, List<String> rootFiles) {
        int storageIndex = chapter.optInt("storageIndex", position + 1);
        if (storageIndex <= 0) storageIndex = position + 1;
        String stem = "chapter-" + (bookId + ":" + storageIndex).replaceAll("[^A-Za-z0-9._-]", "-");
        ArrayList<String> result = new ArrayList<>();
        for (String name : rootFiles) {
            if (name.equals(stem + ".md") || name.equals(stem + ".txt")
                    || name.equals(stem + ".md.txt")
                    || (name.startsWith(stem + ".md (") && name.endsWith(").txt"))) {
                result.add(name);
            }
        }
        return result;
    }

    private static String bestCandidate(List<String> names) {
        String best = null;
        int bestRank = Integer.MIN_VALUE;
        for (String name : names) {
            int rank = 0;
            if (name.endsWith(".md")) rank = 1;
            else if (name.endsWith(".txt") && name.indexOf(".md (") < 0) rank = 2;
            else if (name.endsWith(".md.txt")) rank = 3;
            else {
                int open = name.lastIndexOf(".md (");
                int close = name.lastIndexOf(").txt");
                if (open >= 0 && close > open) {
                    try { rank = 100 + Integer.parseInt(name.substring(open + 5, close)); }
                    catch (NumberFormatException ignored) { rank = 4; }
                }
            }
            if (rank > bestRank) {
                best = name;
                bestRank = rank;
            }
        }
        return best;
    }
}
