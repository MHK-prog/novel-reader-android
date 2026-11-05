package com.novelreader.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Java runtime for the colloquial-core rule format. Downloaded Kotlin is stored, never executed. */
final class ColloquialPersianTransformer {
    private static final char ZWNJ = '\u200c';
    private static final String[] VERB_PREFIXES = {"می‌", "نمی‌", "ب"};
    private static final String[] FORMAL_ENDINGS = {"م", "ی", "د", "یم", "ید", "ند"};
    private static final String[] COLLOQUIAL_ENDINGS = {"م", "ی", "ه", "یم", "ین", "ن"};
    private static String cachedRulesJson;
    private static PreparedRules cachedRules;

    private ColloquialPersianTransformer() { }

    static String transform(String text, String rulesJson) throws JSONException {
        PreparedRules rules = loadRules(rulesJson);
        String result = replacePairs(text, rules.normalizations, "general", null);
        result = replacePairs(result, rules.prefixes, "prefix", null);
        result = replacePairs(result, rules.phrases, "general", null);
        result = replaceContextualPairs(result, rules.contextualVerbs);
        result = replacePairs(result, rules.suffixes, "suffix", null);
        result = replacePairs(result, rules.words, "word", rules.suffixes);
        return replacePairs(result, rules.verbs, "general", null);
    }

    static synchronized void prepare(String rulesJson) throws JSONException {
        loadRules(rulesJson);
    }

    private static synchronized PreparedRules loadRules(String rulesJson) throws JSONException {
        if (rulesJson.equals(cachedRulesJson) && cachedRules != null) return cachedRules;
        JSONObject rules = new JSONObject(rulesJson);
        List<Pair> suffixes = readPairs(rules.optJSONArray("suffixes"));
        PreparedRules prepared = new PreparedRules(
                readPairs(rules.optJSONArray("normalizations")),
                readPairs(rules.optJSONArray("prefixes")),
                readPairs(rules.optJSONArray("phrases")),
                readContextualPairs(rules.optJSONArray("contextualVerbs")),
                suffixes,
                readPairs(rules.optJSONArray("words")),
                expandVerbStems(readPairs(rules.optJSONArray("verbs")), rules.optJSONArray("verb_stems"))
        );
        cachedRulesJson = rulesJson;
        cachedRules = prepared;
        return prepared;
    }

    private static List<Pair> expandVerbStems(List<Pair> manual, JSONArray stems) {
        List<Pair> result = new ArrayList<>(manual);
        Set<String> seenSources = new HashSet<>();
        for (Pair pair : manual) seenSources.add(pair.source);
        if (stems == null) return result;

        for (int i = 0; i < stems.length(); i++) {
            JSONObject stem = stems.optJSONObject(i);
            if (stem == null) continue;
            String formal = stem.optString("f", "");
            String colloquial = stem.optString("c", "");
            if (formal.isEmpty() || colloquial.isEmpty()) continue;
            String linkedFormal = formal + stem.optString("link", "");
            for (String prefix : VERB_PREFIXES) {
                for (int ending = 0; ending < FORMAL_ENDINGS.length; ending++) {
                    String source = prefix + linkedFormal + FORMAL_ENDINGS[ending];
                    String replacement = prefix + colloquial + COLLOQUIAL_ENDINGS[ending];
                    // An explicitly listed verb always overrides a generated form.
                    if (!source.equals(replacement) && seenSources.add(source)) {
                        result.add(new Pair(source, replacement));
                    }
                }
            }
        }
        return result;
    }

    private static final class PreparedRules {
        final List<Pair> normalizations;
        final List<Pair> prefixes;
        final List<Pair> phrases;
        final List<ContextualPair> contextualVerbs;
        final List<Pair> suffixes;
        final List<Pair> words;
        final List<Pair> verbs;

        PreparedRules(List<Pair> normalizations, List<Pair> prefixes, List<Pair> phrases,
                      List<ContextualPair> contextualVerbs, List<Pair> suffixes,
                      List<Pair> words, List<Pair> verbs) {
            this.normalizations = normalizations;
            this.prefixes = prefixes;
            this.phrases = phrases;
            this.contextualVerbs = contextualVerbs;
            this.suffixes = suffixes;
            this.words = words;
            this.verbs = verbs;
        }
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

    private static List<ContextualPair> readContextualPairs(JSONArray array) {
        List<ContextualPair> pairs = new ArrayList<>();
        if (array == null) return pairs;
        for (int i = 0; i < array.length(); i++) {
            JSONArray item = array.optJSONArray(i);
            if (item == null || item.length() < 3) continue;
            String source = item.optString(0, "");
            String replacement = item.optString(1, "");
            JSONArray contextsArray = item.optJSONArray(2);
            if (source.isEmpty() || replacement.isEmpty() || contextsArray == null) continue;
            List<String> contexts = new ArrayList<>();
            for (int j = 0; j < contextsArray.length(); j++) {
                String context = contextsArray.optString(j, "").trim();
                if (!context.isEmpty()) contexts.add(context);
            }
            if (!contexts.isEmpty()) pairs.add(new ContextualPair(source, replacement, contexts));
        }
        return pairs;
    }

    private static String replaceContextualPairs(String text, List<ContextualPair> pairs) {
        String result = text;
        for (ContextualPair pair : pairs) {
            List<String> contexts = new ArrayList<>(pair.contexts);
            contexts.sort((left, right) -> Integer.compare(right.length(), left.length()));
            for (String context : contexts) {
                String regex = "(?<![\\p{L}\\p{N}\\u200c])(" + flexiblePattern(context, false)
                        + ")([ \\u200c]+)" + flexiblePattern(pair.source, false)
                        + "(?![\\p{L}\\p{N}\\u200c])";
                Matcher matcher = Pattern.compile(regex).matcher(result);
                StringBuffer output = new StringBuffer();
                while (matcher.find()) {
                    String replacement = matcher.group(1) + matcher.group(2) + pair.replacement;
                    matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
                }
                matcher.appendTail(output);
                result = output.toString();
            }
        }
        return result;
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

    private static final class ContextualPair {
        final String source;
        final String replacement;
        final List<String> contexts;

        ContextualPair(String source, String replacement, List<String> contexts) {
            this.source = source;
            this.replacement = replacement;
            this.contexts = contexts;
        }
    }
}
