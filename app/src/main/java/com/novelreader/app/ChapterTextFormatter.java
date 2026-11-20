package com.novelreader.app;

/** Small plain-text helpers for Chapters. */
final class ChapterTextFormatter {
    private ChapterTextFormatter() { }

    static long countWords(String value) {
        if (value == null || value.isEmpty()) return 0;
        long count = 0;
        boolean inWord = false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            boolean wordCharacter = Character.isLetterOrDigit(codePoint) || codePoint == 0x200C;
            if (wordCharacter && !inWord) count++;
            inWord = wordCharacter;
            offset += Character.charCount(codePoint);
        }
        return count;
    }
}
