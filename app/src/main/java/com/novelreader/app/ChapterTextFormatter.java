package com.novelreader.app;

/** Pure text helpers shared by the reader, Markdown preview, and colloquializer. */
final class ChapterTextFormatter {
    static final String MARKDOWN_END_TOKEN = "\uE000END\uE001";
    static final String MARKDOWN_CONTINUE_TOKEN = "\uE000CONTINUE\uE001";
    static final String MARKDOWN_RULE_TOKEN = "\uE000RULE\uE001";

    private ChapterTextFormatter() { }

    static String markdownSource(String source) {
        String[] lines = visibleText(source).split("\\n", -1);
        StringBuilder result = new StringBuilder(source == null ? 0 : source.length());
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().equals("---")) line = MARKDOWN_RULE_TOKEN;
            else line = line.replace("%end%", MARKDOWN_END_TOKEN)
                    .replace("%continue%", MARKDOWN_CONTINUE_TOKEN);
            if (i > 0) result.append('\n');
            result.append(line);
        }
        return result.toString();
    }

    static String visibleText(String text) {
        if (text == null) return "";
        StringBuilder visible = new StringBuilder(text.length());
        for (int index = 0; index < text.length();) {
            int annotationEnd = annotationEnd(text, index);
            if (annotationEnd > index) index = annotationEnd;
            else visible.append(text.charAt(index++));
        }
        return visible.toString();
    }

    static int sourceOffsetForVisibleOffset(String source, int targetVisibleOffset) {
        if (source == null) return 0;
        int visibleCount = 0;
        for (int index = 0; index < source.length();) {
            int annotationEnd = annotationEnd(source, index);
            if (annotationEnd > index) {
                index = annotationEnd;
                continue;
            }
            if (visibleCount >= targetVisibleOffset) return index;
            visibleCount++;
            index++;
        }
        return source.length();
    }

    static int annotationEnd(String text, int start) {
        if (text == null || start < 0 || start >= text.length() || text.charAt(start) != '{') return -1;
        int close = text.indexOf('}', start + 1);
        if (close <= start + 1 || close + 1 >= text.length()) return -1;
        return Character.isLetterOrDigit(text.codePointAt(close + 1)) ? close + 1 : -1;
    }

    static boolean containsAnnotations(String text) {
        if (text == null) return false;
        for (int index = 0; index < text.length(); index++) {
            if (annotationEnd(text, index) > index) return true;
        }
        return false;
    }

    static String stripAnnotations(String text) {
        if (text == null || text.isEmpty()) return text == null ? "" : text;
        StringBuilder output = new StringBuilder(text.length());
        for (int index = 0; index < text.length();) {
            int end = annotationEnd(text, index);
            if (end > index) index = end;
            else output.append(text.charAt(index++));
        }
        return output.toString();
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
