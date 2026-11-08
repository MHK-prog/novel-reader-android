package com.novelreader.app;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** Shared host capability for literal replacement pairs supplied by extensions. */
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
            Rule rule = new Rule(source, pair.optString(1));
            if (rule.matchSource.isEmpty()) throw new JSONException("Rule source must contain a matchable character.");
            rules.add(rule);
        }
        rules.sort((left, right) -> Integer.compare(right.matchSource.length(), left.matchSource.length()));
        cachedRulesJson = rulesJson;
        cachedRules = rules;
        return rules;
    }

    private static boolean isSeparator(char character) {
        return character == ' ' || character == '\u200c';
    }

    private static char foldCharacter(char character) {
        return character == '\u0622' ? '\u0627' : character;
    }

    private static String matchKey(String source) {
        StringBuilder key = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++) {
            char character = source.charAt(i);
            if (!isSeparator(character)) key.append(foldCharacter(character));
        }
        return key.toString();
    }

    private static int matchEnd(String text, int start, String source) {
        int textIndex = start;
        for (int ruleIndex = 0; ruleIndex < source.length(); ruleIndex++) {
            char expected = source.charAt(ruleIndex);
            if (isSeparator(expected)) {
                int separatorStart = textIndex;
                while (textIndex < text.length() && isSeparator(text.charAt(textIndex))) textIndex++;
                boolean isSourceEdge = ruleIndex == 0 || ruleIndex == source.length() - 1;
                if (isSourceEdge && textIndex == separatorStart) return -1;
                continue;
            }
            if (textIndex >= text.length() || isSeparator(text.charAt(textIndex))
                    || foldCharacter(text.charAt(textIndex)) != foldCharacter(expected)) return -1;
            textIndex++;
        }
        return textIndex;
    }

    private static String transform(String text, List<Rule> rules) {
        String sourceText = text == null ? "" : text;
        for (Rule rule : rules) {
            if (rule.source.length() == 1) sourceText = sourceText.replace(rule.source, rule.replacement);
        }
        List<Rule> textRules = new ArrayList<>();
        for (Rule rule : rules) if (rule.source.length() != 1) textRules.add(rule);

        StringBuilder result = new StringBuilder(sourceText.length());
        int originalCursor = 0;
        for (int index = 0; index < sourceText.length();) {
            Rule matchedRule = null;
            int matchEndIndex = -1;
            for (Rule rule : textRules) {
                int end = matchEnd(sourceText, index, rule.source);
                if (end >= 0) {
                    matchedRule = rule;
                    matchEndIndex = end;
                    break;
                }
            }
            if (matchedRule == null) {
                index++;
            } else {
                result.append(sourceText, originalCursor, index);
                result.append(matchedRule.replacement);
                originalCursor = matchEndIndex;
                index = matchEndIndex;
            }
        }
        result.append(sourceText, originalCursor, sourceText.length());
        return result.toString();
    }

    private static final class Rule {
        final String source;
        final String replacement;
        final String matchSource;

        Rule(String source, String replacement) {
            this.source = source;
            this.replacement = replacement;
            this.matchSource = matchKey(source);
        }
    }
}
