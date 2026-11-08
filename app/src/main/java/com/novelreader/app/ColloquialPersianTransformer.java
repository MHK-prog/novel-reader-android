package com.novelreader.app;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** Applies literal replacement pairs with separator-tolerant Persian key matching. */
final class ColloquialPersianTransformer {
    private static String cachedRulesJson;
    private static List<Rule> cachedRules;

    private ColloquialPersianTransformer() { }

    static String transform(String text, String rulesJson) throws JSONException {
        return transform(text, loadRules(rulesJson));
    }

    static synchronized void prepare(String rulesJson) throws JSONException {
        loadRules(rulesJson);
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
            Rule rule = new Rule(source, pair.optString(1));
            if (rule.matchSource.isEmpty()) throw new JSONException("Rule source must contain a matchable character.");
            rules.add(rule);
        }
        rules.sort((left, right) -> Integer.compare(right.matchSource.length(), left.matchSource.length()));
        cachedRulesJson = rulesJson;
        cachedRules = rules;
        return rules;
    }

    private static String normalizeKey(String value) {
        StringBuilder key = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == ' ' || character == '\u200c') continue;
            key.append(character == '\u0622' ? '\u0627' : character);
        }
        return key.toString();
    }

    private static String transform(String text, List<Rule> rules) {
        String sourceText = text == null ? "" : text;
        for (Rule rule : rules) {
            if (rule.source.length() == 1) sourceText = sourceText.replace(rule.source, rule.replacement);
        }

        StringBuilder normalized = new StringBuilder(sourceText.length());
        int[] starts = new int[sourceText.length()];
        int[] ends = new int[sourceText.length()];
        int normalizedLength = 0;
        for (int i = 0; i < sourceText.length(); i++) {
            char character = sourceText.charAt(i);
            if (character == ' ' || character == '\u200c') continue;
            normalized.append(character == '\u0622' ? '\u0627' : character);
            starts[normalizedLength] = i;
            ends[normalizedLength] = i + 1;
            normalizedLength++;
        }

        StringBuilder result = new StringBuilder(sourceText.length());
        int originalCursor = 0;
        for (int index = 0; index < normalizedLength;) {
            Rule match = null;
            for (Rule rule : rules) {
                if (rule.source.length() != 1 && startsWith(normalized, rule.matchSource, index)) {
                    match = rule;
                    break;
                }
            }
            if (match == null) {
                index++;
            } else {
                int originalStart = starts[index];
                int originalEnd = ends[index + match.matchSource.length() - 1];
                result.append(sourceText, originalCursor, originalStart);
                result.append(match.replacement);
                originalCursor = originalEnd;
                index += match.matchSource.length();
            }
        }
        result.append(sourceText, originalCursor, sourceText.length());
        return result.toString();
    }

    private static boolean startsWith(StringBuilder text, String value, int offset) {
        if (offset + value.length() > text.length()) return false;
        for (int i = 0; i < value.length(); i++) {
            if (text.charAt(offset + i) != value.charAt(i)) return false;
        }
        return true;
    }

    private static final class Rule {
        final String source;
        final String replacement;
        final String matchSource;

        Rule(String source, String replacement) {
            this.source = source;
            this.replacement = replacement;
            this.matchSource = normalizeKey(source);
        }
    }
}
