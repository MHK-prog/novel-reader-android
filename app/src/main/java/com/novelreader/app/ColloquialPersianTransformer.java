package com.novelreader.app;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** Applies literal source-to-replacement rules without regex or generated patterns. */
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
            rules.add(new Rule(source, pair.optString(1)));
        }
        rules.sort((left, right) -> Integer.compare(right.source.length(), left.source.length()));
        cachedRulesJson = rulesJson;
        cachedRules = rules;
        return rules;
    }

    private static String transform(String text, List<Rule> rules) {
        String sourceText = text == null ? "" : text;
        List<Rule> textRules = new ArrayList<>();
        for (Rule rule : rules) {
            if (rule.source.length() == 1) sourceText = sourceText.replace(rule.source, rule.replacement);
            else textRules.add(rule);
        }
        StringBuilder result = new StringBuilder(sourceText.length());
        for (int index = 0; index < sourceText.length();) {
            Rule match = null;
            for (Rule rule : textRules) {
                if (sourceText.startsWith(rule.source, index)) {
                    match = rule;
                    break;
                }
            }
            if (match == null) result.append(sourceText.charAt(index++));
            else {
                result.append(match.replacement);
                index += match.source.length();
            }
        }
        return result.toString();
    }

    private static final class Rule {
        final String source;
        final String replacement;

        Rule(String source, String replacement) {
            this.source = source;
            this.replacement = replacement;
        }
    }
}
