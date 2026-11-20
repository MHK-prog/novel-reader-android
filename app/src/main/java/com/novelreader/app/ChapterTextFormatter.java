package com.novelreader.app;

/** Small plain-text helpers for Chapters and their one-time legacy conversion. */
final class ChapterTextFormatter {
    static final class CleanText {
        final String text;
        final boolean end;

        CleanText(String text, boolean end) {
            this.text = text;
            this.end = end;
        }
    }

    private ChapterTextFormatter() { }

    /** Removes old reader-only markers while preserving the novel text and end state. */
    static CleanText cleanLegacyText(String source) {
        if (source == null || source.isEmpty()) return new CleanText("", false);
        String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder result = new StringBuilder(source.length());
        boolean end = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.contains("%end%")) end = true;
            if (trimmed.equals("---") || trimmed.equals("%continue%") || trimmed.equals("%end%")) {
                if (i < lines.length - 1 && result.length() > 0 && result.charAt(result.length() - 1) != '\n') {
                    result.append('\n');
                }
                continue;
            }

            line = line.replace("%end%", "").replace("%continue%", "");
            line = removeBraceAnnotations(line);
            result.append(line);
            if (i < lines.length - 1) result.append('\n');
        }
        return new CleanText(result.toString(), end);
    }

    private static String removeBraceAnnotations(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length();) {
            if (text.charAt(i) == '{') {
                int close = text.indexOf('}', i + 1);
                if (close >= 0) {
                    i = close + 1;
                    continue;
                }
            }
            result.append(text.charAt(i++));
        }
        return result.toString();
    }

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
