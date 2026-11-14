package com.novelreader.app;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compiles lightweight literal and variable replacement rules once, then scans the original text. */
final class TextReplacementEngine {
    private static String cachedRulesJson;
    private static CompiledRules cachedRules;

    private TextReplacementEngine() { }

    static String transform(String text, String rulesJson) throws JSONException {
        return transform(text, loadRules(rulesJson), false);
    }

    static String transform(String text, String rulesJson, boolean tagVariableChanges) throws JSONException {
        return transform(text, loadRules(rulesJson), tagVariableChanges);
    }

    private static synchronized CompiledRules loadRules(String rulesJson) throws JSONException {
        if (rulesJson.equals(cachedRulesJson) && cachedRules != null) return cachedRules;

        JSONArray input = new JSONArray(rulesJson);
        ArrayList<RawRule> rawRules = new ArrayList<>(input.length());
        ArrayList<VerbStem> verbStems = new ArrayList<>();
        for (int i = 0; i < input.length(); i++) {
            JSONArray pair = input.optJSONArray(i);
            if (pair == null || pair.length() < 2 || pair.length() > 3
                    || !(pair.opt(0) instanceof String) || !(pair.opt(1) instanceof String)) {
                throw new JSONException("Each rule must contain source, replacement, and optional exceptions.");
            }
            String source = pair.optString(0);
            String replacement = pair.optString(1);
            if (source.isEmpty()) throw new JSONException("Rule source cannot be empty.");
            RawRule raw = new RawRule(source, replacement);
            JSONArray exceptionArray = pair.optJSONArray(2);
            if (pair.length() == 3 && exceptionArray == null) {
                throw new JSONException("Rule exceptions must be an array of strings.");
            }
            if (exceptionArray != null) {
                for (int j = 0; j < exceptionArray.length(); j++) {
                    Object exception = exceptionArray.opt(j);
                    if (!(exception instanceof String) || ((String) exception).trim().isEmpty()) {
                        throw new JSONException("Rule exceptions must be non-empty strings.");
                    }
                    raw.exceptions.add(((String) exception).trim());
                }
            }

            if (isVerbStemEntry(source, replacement)) {
                String past = source.substring(2);
                String colloquial = replacement.substring(2);
                if (!isArabicWord(past) || !isArabicWord(colloquial)) {
                    throw new JSONException("Verb entries must use vgPast → vhColloquial, for example vgکرد → vhکن.");
                }
                verbStems.add(new VerbStem(past, colloquial));
            } else {
                rawRules.add(raw);
            }
        }

        ArrayList<String> nouns = new ArrayList<>();
        Set<String> uniqueNouns = new HashSet<>();
        for (RawRule raw : rawRules) {
            if (isArabicWord(raw.source) && uniqueNouns.add(raw.source)) nouns.add(raw.source);
        }
        nouns.sort((left, right) -> Integer.compare(right.length(), left.length()));
        Map<Character, List<String>> nounsByFirstCharacter = new HashMap<>();
        for (String noun : nouns) {
            char first = foldCharacter(noun.charAt(0));
            nounsByFirstCharacter.computeIfAbsent(first, ignored -> new ArrayList<>()).add(noun);
        }
        verbStems.sort((left, right) -> Integer.compare(right.past.length(), left.past.length()));

        ArrayList<Rule> rules = new ArrayList<>(rawRules.size());
        for (RawRule raw : rawRules) {
            boolean hasPastVariable = raw.source.contains("vg");
            boolean hasPresentVariable = raw.replacement.contains("vh");
            if (hasPastVariable != hasPresentVariable
                    || countPair(raw.source, "vg") > 1 || countPair(raw.replacement, "vh") > 1) {
                throw new JSONException("Verb rules must pair one vg source with one vh destination.");
            }
            boolean tagPattern = hasAsciiLetter(raw.source.replace("b", ""));
            if (hasPastVariable) {
                for (VerbStem stem : verbStems) {
                    rules.add(compileRule(raw.source.replace("vg", stem.past),
                            raw.replacement.replace("vh", stem.colloquial), raw.exceptions,
                            nounsByFirstCharacter, tagPattern));
                }
            } else {
                if (raw.replacement.contains("vh")) {
                    throw new JSONException("vh can only be used with vg in the source.");
                }
                rules.add(compileRule(raw.source, raw.replacement, raw.exceptions,
                        nounsByFirstCharacter, tagPattern));
            }
        }

        rules.sort((left, right) -> Integer.compare(right.priorityLength, left.priorityLength));
        CompiledRules compiled = new CompiledRules(rules);
        cachedRulesJson = rulesJson;
        cachedRules = compiled;
        return compiled;
    }

    private static int countPair(String value, String token) {
        int count = 0;
        for (int index = value.indexOf(token); index >= 0; index = value.indexOf(token, index + token.length())) count++;
        return count;
    }

    private static boolean isVerbStemEntry(String source, String replacement) {
        return source.startsWith("vg") && replacement.startsWith("vh")
                && countPair(source, "vg") == 1 && countPair(replacement, "vh") == 1;
    }

    private static Rule compileRule(String source, String replacement, List<String> exceptions,
                                    Map<Character, List<String>> nouns, boolean tagPattern) throws JSONException {
        ArrayList<Segment> segments = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        boolean ignoreBoundaries = false;
        int xCount = 0, aCount = 0, nCount = 0, sCount = 0;
        for (int index = 0; index < source.length();) {
            char character = source.charAt(index);
            if (character == 'b') {
                ignoreBoundaries = true;
                index++;
                continue;
            }
            SegmentType type = null;
            if (character == 'x') { type = SegmentType.WILDCARD; xCount++; }
            else if (character == 'a') { type = SegmentType.NOUN; aCount++; }
            else if (character == 'n') { type = SegmentType.NEGATION; nCount++; }
            else if (character == 's') { type = SegmentType.SEPARATOR; sCount++; }
            if (type == null) {
                literal.append(character);
                index++;
                continue;
            }
            flushLiteral(segments, literal);
            segments.add(new Segment(type, null));
            index++;
        }
        flushLiteral(segments, literal);
        if (segments.isEmpty()) throw new JSONException("Rule source must contain a matchable character.");
        int minimumWidth = 0;
        for (Segment segment : segments) {
            if (segment.type == SegmentType.LITERAL) minimumWidth += segment.literal.length();
            else if (segment.type == SegmentType.WILDCARD || segment.type == SegmentType.NOUN) minimumWidth++;
        }
        if (minimumWidth == 0) throw new JSONException("A rule must contain at least one required character.");
        if (xCount > 1 || aCount > 1 || nCount > 1 || sCount > 1) {
            throw new JSONException("Each rule can use x, a, n, and s at most once.");
        }
        if (replacement.indexOf('x') >= 0 && xCount == 0
                || replacement.indexOf('a') >= 0 && aCount == 0
                || replacement.indexOf('n') >= 0 && nCount == 0
                || replacement.indexOf('s') >= 0 && sCount == 0) {
            throw new JSONException("Destination variables must also appear in the source.");
        }
        return new Rule(source, replacement, segments, new ArrayList<>(exceptions), nouns,
                ignoreBoundaries, tagPattern);
    }

    private static void flushLiteral(List<Segment> segments, StringBuilder literal) {
        if (literal.length() == 0) return;
        segments.add(new Segment(SegmentType.LITERAL, literal.toString()));
        literal.setLength(0);
    }

    private static boolean hasAsciiLetter(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) return true;
        }
        return false;
    }

    private static boolean isArabicWord(String value) {
        if (value.isEmpty()) return false;
        boolean hasLetter = false;
        for (int i = 0; i < value.length();) {
            int codePoint = value.codePointAt(i);
            if (isArabicLetter(codePoint)) hasLetter = true;
            else if (codePoint != 0x200c && !isArabicMark(codePoint)) return false;
            i += Character.charCount(codePoint);
        }
        return hasLetter;
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

    private static char matchKeyCharacter(char character) {
        return foldCharacter(character);
    }

    private static int matchLiteralEnd(String text, int start, String source) {
        int textIndex = start;
        for (int ruleIndex = 0; ruleIndex < source.length(); ruleIndex++) {
            if (textIndex >= text.length()
                    || matchKeyCharacter(text.charAt(textIndex)) != matchKeyCharacter(source.charAt(ruleIndex))) return -1;
            textIndex++;
        }
        return textIndex;
    }

    private static boolean isContextSeparator(int codePoint) {
        return codePoint == 0x200c || Character.isWhitespace(codePoint);
    }

    private static boolean matchesAdjacentException(String text, int start, int end, String exception) {
        int after = end;
        while (after < text.length()) {
            int codePoint = text.codePointAt(after);
            if (!isContextSeparator(codePoint)) break;
            after += Character.charCount(codePoint);
        }
        int afterEnd = matchLiteralEnd(text, after, exception);
        if (afterEnd >= 0 && hasWordBoundary(text, after) && hasWordBoundary(text, afterEnd)) return true;

        int before = start;
        while (before > 0) {
            int codePoint = text.codePointBefore(before);
            if (!isContextSeparator(codePoint)) break;
            before -= Character.charCount(codePoint);
        }
        int beforeStart = before - exception.length();
        if (beforeStart < 0) return false;
        int beforeEnd = matchLiteralEnd(text, beforeStart, exception);
        return beforeEnd == before && hasWordBoundary(text, beforeStart) && hasWordBoundary(text, beforeEnd);
    }

    private static boolean hasAdjacentException(String text, int start, int end, List<String> exceptions) {
        for (String exception : exceptions) {
            if (matchesAdjacentException(text, start, end, exception)) return true;
        }
        return false;
    }

    private static Match matchAt(String text, int start, Rule rule) {
        Captures captures = matchSegments(text, start, rule, 0, new Captures());
        return captures == null ? null : new Match(captures.end, captures);
    }

    private static Captures matchSegments(String text, int textIndex, Rule rule, int segmentIndex,
                                         Captures captures) {
        if (segmentIndex >= rule.segments.size()) {
            Captures result = captures.copy();
            result.end = textIndex;
            return result;
        }
        Segment segment = rule.segments.get(segmentIndex);
        switch (segment.type) {
            case LITERAL: {
                int end = matchLiteralEnd(text, textIndex, segment.literal);
                return end < 0 ? null : matchSegments(text, end, rule, segmentIndex + 1, captures);
            }
            case NEGATION: {
                if (textIndex < text.length() && text.charAt(textIndex) == '\u0646') {
                    Captures withNegation = captures.copy();
                    withNegation.negation = "\u0646";
                    Captures match = matchSegments(text, textIndex + 1, rule, segmentIndex + 1, withNegation);
                    if (match != null) return match;
                }
                return matchSegments(text, textIndex, rule, segmentIndex + 1, captures);
            }
            case SEPARATOR: {
                int end = textIndex;
                while (end < text.length()) {
                    int codePoint = text.codePointAt(end);
                    if (!isContextSeparator(codePoint)) break;
                    end += Character.charCount(codePoint);
                }
                Captures withSeparator = captures.copy();
                withSeparator.separator = text.substring(textIndex, end);
                return matchSegments(text, end, rule, segmentIndex + 1, withSeparator);
            }
            case NOUN: {
                List<String> nouns = textIndex < text.length()
                        ? rule.nouns.get(foldCharacter(text.charAt(textIndex))) : null;
                if (nouns == null) return null;
                for (String noun : nouns) {
                    int end = matchLiteralEnd(text, textIndex, noun);
                    if (end < 0) continue;
                    Captures withNoun = captures.copy();
                    withNoun.noun = text.substring(textIndex, end);
                    Captures match = matchSegments(text, end, rule, segmentIndex + 1, withNoun);
                    if (match != null) return match;
                }
                return null;
            }
            case WILDCARD: {
                int cursor = textIndex;
                boolean hasArabic = false;
                ArrayList<Integer> ends = new ArrayList<>();
                while (cursor < text.length()) {
                    int codePoint = text.codePointAt(cursor);
                    if (!isWildcardCharacter(codePoint)) break;
                    if (isArabicLetter(codePoint)) hasArabic = true;
                    cursor += Character.charCount(codePoint);
                    if (hasArabic) ends.add(cursor);
                }
                for (int i = ends.size() - 1; i >= 0; i--) {
                    int end = ends.get(i);
                    Captures withWildcard = captures.copy();
                    withWildcard.wildcard = text.substring(textIndex, end);
                    Captures match = matchSegments(text, end, rule, segmentIndex + 1, withWildcard);
                    if (match != null) return match;
                }
                return null;
            }
            default: return null;
        }
    }

    private static boolean isArabicLetter(int codePoint) {
        return Character.isLetter(codePoint)
                && Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.ARABIC;
    }

    private static boolean isArabicMark(int codePoint) {
        int type = Character.getType(codePoint);
        return (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK)
                && Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.ARABIC;
    }

    private static boolean isWildcardCharacter(int codePoint) {
        return codePoint == 0x200c || isArabicLetter(codePoint) || isArabicMark(codePoint);
    }

    private static String expandReplacement(Rule rule, Captures captures) {
        StringBuilder output = new StringBuilder(rule.replacement.length() + 8);
        for (int i = 0; i < rule.replacement.length(); i++) {
            char character = rule.replacement.charAt(i);
            if (character == 'x') output.append(captures.wildcard == null ? "" : captures.wildcard);
            else if (character == 'a') output.append(captures.noun == null ? "" : captures.noun);
            else if (character == 'n') output.append(captures.negation);
            else if (character == 's') output.append(captures.separator);
            else output.append(character);
        }
        return output.toString();
    }

    private static String transform(String text, CompiledRules compiled, boolean tagVariableChanges) {
        String sourceText = text == null ? "" : text;
        if (compiled.rules.isEmpty() || sourceText.isEmpty()) return sourceText;
        StringBuilder result = new StringBuilder(sourceText.length());
        int originalCursor = 0;
        for (int index = 0; index < sourceText.length();) {
            Rule matchedRule = null;
            Match matched = null;
            char key = foldCharacter(sourceText.charAt(index));
            List<Rule> indexed = compiled.byFirstCharacter.get(key);
            int indexedIndex = 0;
            int flexibleIndex = 0;
            while ((indexed != null && indexedIndex < indexed.size())
                    || flexibleIndex < compiled.flexibleRules.size()) {
                Rule rule;
                if (indexed == null || indexedIndex >= indexed.size()) {
                    rule = compiled.flexibleRules.get(flexibleIndex++);
                } else if (flexibleIndex >= compiled.flexibleRules.size()) {
                    rule = indexed.get(indexedIndex++);
                } else if (indexed.get(indexedIndex).priorityLength >= compiled.flexibleRules.get(flexibleIndex).priorityLength) {
                    rule = indexed.get(indexedIndex++);
                } else {
                    rule = compiled.flexibleRules.get(flexibleIndex++);
                }
                if (!rule.ignoreBoundaries && !hasWordBoundary(sourceText, index)) continue;
                Match candidate = matchAt(sourceText, index, rule);
                if (candidate != null && (rule.ignoreBoundaries || hasWordBoundary(sourceText, candidate.end))
                        && !hasAdjacentException(sourceText, index, candidate.end, rule.exceptions)) {
                    matchedRule = rule;
                    matched = candidate;
                    break;
                }
            }
            if (matchedRule == null) {
                index++;
                continue;
            }

            result.append(sourceText, originalCursor, index);
            String replacement = expandReplacement(matchedRule, matched.captures);
            if (tagVariableChanges && matchedRule.hasAsciiKey) {
                result.append("%hid{").append(sourceText, index, matched.end).append("}%");
            }
            result.append(replacement);
            originalCursor = matched.end;
            index = matched.end;
        }
        result.append(sourceText, originalCursor, sourceText.length());
        return result.toString();
    }

    private static final class CompiledRules {
        final List<Rule> rules;
        final Map<Character, List<Rule>> byFirstCharacter = new HashMap<>();
        final List<Rule> flexibleRules = new ArrayList<>();

        CompiledRules(List<Rule> rules) {
            this.rules = Collections.unmodifiableList(rules);
            for (Rule rule : rules) {
                boolean indexed = false;
                boolean needsFlexibleScan = false;
                for (Segment segment : rule.segments) {
                    if (segment.type == SegmentType.LITERAL) {
                        if (!segment.literal.isEmpty()) addIndex(foldCharacter(segment.literal.charAt(0)), rule);
                        indexed = !segment.literal.isEmpty();
                        break;
                    }
                    if (segment.type == SegmentType.NEGATION) {
                        addIndex('\u0646', rule);
                        indexed = true;
                        continue;
                    }
                    needsFlexibleScan = true;
                    break;
                }
                if (!indexed || needsFlexibleScan) flexibleRules.add(rule);
            }
            for (List<Rule> candidates : byFirstCharacter.values()) {
                candidates.sort((left, right) -> Integer.compare(right.priorityLength, left.priorityLength));
            }
            flexibleRules.sort((left, right) -> Integer.compare(right.priorityLength, left.priorityLength));
        }

        private void addIndex(char character, Rule rule) {
            byFirstCharacter.computeIfAbsent(character, ignored -> new ArrayList<>()).add(rule);
        }
    }

    private enum SegmentType { LITERAL, WILDCARD, NOUN, NEGATION, SEPARATOR }

    private static final class Segment {
        final SegmentType type;
        final String literal;
        Segment(SegmentType type, String literal) { this.type = type; this.literal = literal; }
    }

    private static final class RawRule {
        final String source;
        final String replacement;
        final List<String> exceptions = new ArrayList<>();
        RawRule(String source, String replacement) { this.source = source; this.replacement = replacement; }
    }

    private static final class Rule {
        final String source;
        final String replacement;
        final List<Segment> segments;
        final List<String> exceptions;
        final Map<Character, List<String>> nouns;
        final boolean ignoreBoundaries;
        final boolean hasAsciiKey;
        final int priorityLength;

        Rule(String source, String replacement, List<Segment> segments, List<String> exceptions,
             Map<Character, List<String>> nouns, boolean ignoreBoundaries, boolean hasAsciiKey) {
            this.source = source;
            this.replacement = replacement;
            this.segments = segments;
            this.exceptions = exceptions;
            this.nouns = nouns;
            this.ignoreBoundaries = ignoreBoundaries;
            this.hasAsciiKey = hasAsciiKey;
            this.priorityLength = source.length();
        }
    }

    private static final class Captures {
        String wildcard;
        String noun;
        String negation = "";
        String separator = "";
        int end;

        Captures copy() {
            Captures copy = new Captures();
            copy.wildcard = wildcard;
            copy.noun = noun;
            copy.negation = negation;
            copy.separator = separator;
            copy.end = end;
            return copy;
        }
    }

    private static final class VerbStem {
        final String past;
        final String colloquial;
        VerbStem(String past, String colloquial) { this.past = past; this.colloquial = colloquial; }
    }

    private static final class Match {
        final int end;
        final Captures captures;
        Match(int end, Captures captures) { this.end = end; this.captures = captures; }
    }
}
