package com.novelreader.app;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** Shared host capability for literal and single-x wildcard replacement pairs. */
final class TextReplacementEngine {
    private static String cachedRulesJson;
    private static List<Rule> cachedRules;

    private TextReplacementEngine() { }

    static String transform(String text, String rulesJson) throws JSONException {
        return transform(text, loadRules(rulesJson));
    }

    private static synchronized List<Rule> loadRules(String rulesJson) throws JSONException {
        if (rulesJson.equals(cachedRulesJson) && cachedRules != null) return cachedRules;
        JSONArray input = new JSONArray(rulesJson);
        List<Rule> rules = new ArrayList<>(input.length());
        for (int i = 0; i < input.length(); i++) {
            JSONArray pair = input.optJSONArray(i);
            if (pair == null || pair.length() != 2
                    || !(pair.opt(0) instanceof String) || !(pair.opt(1) instanceof String)) {
                throw new JSONException("Each rule must contain two strings.");
            }
            String source = pair.optString(0);
            if (source.isEmpty()) throw new JSONException("Rule source cannot be empty.");
            int wildcardIndex = source.indexOf('x');
            if (wildcardIndex >= 0 && source.indexOf('x', wildcardIndex + 1) >= 0) {
                throw new JSONException("A rule can contain only one x wildcard.");
            }
            Rule rule = new Rule(source, pair.optString(1), wildcardIndex);
            if (rule.matchSource.isEmpty()) throw new JSONException("Rule source must contain a matchable character.");
            rules.add(rule);
        }
        rules.sort((left, right) -> Integer.compare(right.matchSource.length(), left.matchSource.length()));
        cachedRulesJson = rulesJson;
        cachedRules = rules;
        return rules;
    }

    private static char foldCharacter(char character) {
        return character == '\u0622' ? '\u0627' : character;
    }

    private static boolean isWordCodePoint(int codePoint) {
        if (codePoint == '_' || codePoint == 0x200c) return true;
        int type = Character.getType(codePoint);
        return type == Character.UPPERCASE_LETTER || type == Character.LOWERCASE_LETTER
                || type == Character.TITLECASE_LETTER || type == Character.MODIFIER_LETTER
                || type == Character.OTHER_LETTER || type == Character.DECIMAL_DIGIT_NUMBER
                || type == Character.LETTER_NUMBER || type == Character.OTHER_NUMBER;
    }

    private static boolean hasWordBoundary(String text, int index) {
        boolean beforeIsWord = index > 0 && isWordCodePoint(text.codePointBefore(index));
        boolean afterIsWord = index < text.length() && isWordCodePoint(text.codePointAt(index));
        return beforeIsWord != afterIsWord;
    }

    private static String matchKey(String source) {
        StringBuilder key = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++) {
            key.append(foldCharacter(source.charAt(i)));
        }
        return key.toString();
    }

    private static int matchLiteralEnd(String text, int start, String source) {
        int textIndex = start;
        for (int ruleIndex = 0; ruleIndex < source.length(); ruleIndex++) {
            char expected = source.charAt(ruleIndex);
            if (textIndex >= text.length() || foldCharacter(text.charAt(textIndex)) != foldCharacter(expected)) return -1;
            textIndex++;
        }
        return textIndex;
    }

    private static Match matchAt(String text, int start, Rule rule) {
        if (rule.wildcardIndex < 0) {
            int end = matchLiteralEnd(text, start, rule.source);
            return end < 0 ? null : new Match(end, null);
        }

        String prefix = rule.source.substring(0, rule.wildcardIndex);
        String suffix = rule.source.substring(rule.wildcardIndex + 1);
        int captureStart = matchLiteralEnd(text, start, prefix);
        if (captureStart < 0) return null;

        ArrayList<Integer> ends = new ArrayList<>();
        int cursor = captureStart;
        boolean hasPersianLetter = false;
        while (cursor < text.length()) {
            int codePoint = text.codePointAt(cursor);
            if (!isWildcardCharacter(codePoint)) break;
            if (isArabicLetter(codePoint)) hasPersianLetter = true;
            cursor += Character.charCount(codePoint);
            if (hasPersianLetter) ends.add(cursor);
        }

        for (int i = ends.size() - 1; i >= 0; i--) {
            int captureEnd = ends.get(i);
            int end = matchLiteralEnd(text, captureEnd, suffix);
            if (end >= 0 && hasWordBoundary(text, end)) {
                return new Match(end, text.substring(captureStart, captureEnd));
            }
        }
        return null;
    }

    private static boolean isArabicLetter(int codePoint) {
        return Character.isLetter(codePoint)
                && Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.ARABIC;
    }

    private static boolean isWildcardCharacter(int codePoint) {
        if (codePoint == 0x200c) return true;
        int type = Character.getType(codePoint);
        boolean mark = type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
        return isArabicLetter(codePoint) || (mark
                && Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.ARABIC);
    }

    private static String transform(String text, List<Rule> rules) {
        String sourceText = text == null ? "" : text;

        StringBuilder result = new StringBuilder(sourceText.length());
        int originalCursor = 0;
        for (int index = 0; index < sourceText.length();) {
            Rule matchedRule = null;
            Match matched = null;
            for (Rule rule : rules) {
                if (!hasWordBoundary(sourceText, index)) break;
                Match candidate = matchAt(sourceText, index, rule);
                if (candidate != null && hasWordBoundary(sourceText, candidate.end)) {
                    matchedRule = rule;
                    matched = candidate;
                    break;
                }
            }
            if (matchedRule == null) {
                index++;
            } else {
                result.append(sourceText, originalCursor, index);
                String replacement = matchedRule.replacement;
                if (matchedRule.wildcardIndex >= 0) replacement = replacement.replace("x", matched.capture);
                result.append(replacement);
                originalCursor = matched.end;
                index = matched.end;
            }
        }
        result.append(sourceText, originalCursor, sourceText.length());
        return result.toString();
    }

    private static final class Match {
        final int end;
        final String capture;

        Match(int end, String capture) {
            this.end = end;
            this.capture = capture;
        }
    }

    private static final class Rule {
        final String source;
        final String replacement;
        final String matchSource;
        final int wildcardIndex;

        Rule(String source, String replacement, int wildcardIndex) {
            this.source = source;
            this.replacement = replacement;
            this.matchSource = matchKey(source);
            this.wildcardIndex = wildcardIndex;
        }
    }
}
