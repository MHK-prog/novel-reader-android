package com.novelreader.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Java runtime for the colloquial-core rule format. Downloaded Kotlin is stored, never executed. */
final class ColloquialPersianTransformer {
    private static final char ZWNJ = '\u200c';

    private ColloquialPersianTransformer() { }

    static String transform(String text, String rulesJson) throws JSONException {
        JSONObject rules = new JSONObject(rulesJson);
        List<Pair> suffixes = readPairs(rules.optJSONArray("suffixes"));
        String result = replacePairs(text, readPairs(rules.optJSONArray("normalizations")), "general", null);
        result = replacePairs(result, readPairs(rules.optJSONArray("prefixes")), "prefix", null);
        result = replacePairs(result, readPairs(rules.optJSONArray("phrases")), "general", null);
        result = replacePairs(result, suffixes, "suffix", null);
        result = replacePairs(result, readPairs(rules.optJSONArray("words")), "word", suffixes);
        return replacePairs(result, readPairs(rules.optJSONArray("verbs")), "general", null);
    }

    private static List<Pair> readPairs(JSONArray array) {
        List<Pair> pairs = new ArrayList<>();
        if (array == null) return pairs;
        for (int i = 0; i < array.length(); i++) {
            JSONArray item = array.optJSONArray(i);
            if (item == null || item.length() < 2) continue;
            String source = item.optString(0, "");
            String replacement = item.optString(1, "");
            if (!source.isEmpty() && !source.equals(replacement)) pairs.add(new Pair(source, replacement));
        }
        return pairs;
    }

    private static String replacePairs(String text, List<Pair> pairs, String kind, List<Pair> suffixes) {
        Map<String, String> mapping = new LinkedHashMap<>();
        for (Pair pair : pairs) {
            if (pair.source != null && !pair.source.isEmpty() && !pair.source.equals(pair.replacement)) {
                mapping.put(pair.source, pair.replacement);
            }
        }
        if (mapping.isEmpty()) return text;

        List<String> sources = new ArrayList<>(mapping.keySet());
        Collections.sort(sources, (left, right) -> Integer.compare(right.length(), left.length()));
        boolean anchored = "suffix".equals(kind) || "prefix".equals(kind);
        List<String> alternatives = new ArrayList<>(sources.size());
        for (int i = 0; i < sources.size(); i++) {
            alternatives.add("(" + flexiblePattern(sources.get(i), anchored) + ")");
        }
        String combined = join(alternatives, "|");
        String regex;
        if ("prefix".equals(kind)) {
            regex = "(?<![\\p{L}\\p{N}\\u200c])(?:" + combined + ")(?=[\\p{L}])";
        } else if ("suffix".equals(kind)) {
            regex = "(?:" + combined + ")(?![\\p{L}\\p{N}\\u200c])";
        } else if ("word".equals(kind)) {
            List<String> joinedSuffixes = new ArrayList<>();
            if (suffixes != null) for (Pair suffix : suffixes) {
                if (suffix.source.startsWith(String.valueOf(ZWNJ))) {
                    joinedSuffixes.add(flexiblePattern(suffix.source.substring(1), false));
                }
            }
            String extra = joinedSuffixes.isEmpty() ? ""
                    : "|(?=(?:" + join(joinedSuffixes, "|") + ")(?![\\p{L}\\p{N}\\u200c]))";
            regex = "(?<![\\p{L}\\p{N}\\u200c])(?:" + combined + ")((?![\\p{L}\\p{N}])" + extra + ")";
        } else {
            regex = "(?<![\\p{L}\\p{N}\\u200c])(?:" + combined + ")(?![\\p{L}\\p{N}\\u200c])";
        }

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group();
            for (int i = 0; i < sources.size(); i++) {
                if (matcher.group(i + 1) != null) {
                    replacement = mapping.get(sources.get(i));
                    break;
                }
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static String flexiblePattern(String source, boolean anchorStart) {
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char value = source.charAt(i);
            if (value == ZWNJ) {
                pattern.append(anchorStart && i == 0 ? String.valueOf(ZWNJ) : "[ \\u200c]?");
            } else {
                pattern.append(Pattern.quote(String.valueOf(value)));
            }
        }
        return pattern.toString();
    }

    private static String join(List<String> values, String separator) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(separator);
            result.append(value);
        }
        return result.toString();
    }

    private static final class Pair {
        final String source;
        final String replacement;

        Pair(String source, String replacement) {
            this.source = source;
            this.replacement = replacement;
        }
    }
}
