package com.novelreader.app;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Small literal replacement engine with the x, s, and n variables used by the colloquializer. */
final class TextReplacementEngine {
    private static String cachedJson;
    private static CompiledRules cachedRules;

    private TextReplacementEngine() { }

    static String transform(String text, String rulesJson) throws JSONException {
        return transform(text, rulesJson, false);
    }

    static String transform(String text, String rulesJson, boolean tagChanges) throws JSONException {
        return transformText(text, loadRules(rulesJson), tagChanges);
    }

    private static synchronized CompiledRules loadRules(String json) throws JSONException {
        String sourceJson = json == null ? "[]" : json;
        if (sourceJson.equals(cachedJson) && cachedRules != null) return cachedRules;

        JSONArray input = new JSONArray(sourceJson);
        ArrayList<Rule> rules = new ArrayList<>(input.length());
        for (int i = 0; i < input.length(); i++) {
            JSONArray pair = input.optJSONArray(i);
            if (pair == null || pair.length() < 2 || pair.length() > 3
                    || !(pair.opt(0) instanceof String) || !(pair.opt(1) instanceof String)) {
                throw new JSONException("Each rule must contain source, destination, and optional exceptions.");
            }
            String source = pair.optString(0);
            String replacement = pair.optString(1);
            if (source.trim().isEmpty()) throw new JSONException("Rule source cannot be empty.");

            ArrayList<String> exceptions = new ArrayList<>();
            JSONArray exceptionList = pair.optJSONArray(2);
            if (pair.length() == 3 && exceptionList == null) {
                throw new JSONException("Rule exceptions must be an array of strings.");
            }
            if (exceptionList != null) for (int j = 0; j < exceptionList.length(); j++) {
                Object item = exceptionList.opt(j);
                if (!(item instanceof String) || ((String) item).trim().isEmpty()) {
                    throw new JSONException("Rule exceptions must be non-empty strings.");
                }
                exceptions.add(((String) item).trim());
            }
            rules.add(compileRule(source, replacement, exceptions));
        }
        rules.sort((left, right) -> Integer.compare(right.priority, left.priority));
        cachedRules = new CompiledRules(rules);
        cachedJson = sourceJson;
        return cachedRules;
    }

    private static Rule compileRule(String source, String replacement, List<String> exceptions)
            throws JSONException {
        ArrayList<Segment> segments = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int xCount = 0;
        int sCount = 0;
        int nCount = 0;
        int minimumWidth = 0;

        for (int i = 0; i < source.length(); i++) {
            char character = source.charAt(i);
            SegmentType variable = character == 'x' ? SegmentType.WILDCARD
                    : character == 's' ? SegmentType.SEPARATOR
                    : character == 'n' ? SegmentType.NEGATION : null;
            if (variable == null) {
                literal.append(character);
                minimumWidth++;
                continue;
            }
            flushLiteral(segments, literal);
            segments.add(new Segment(variable, null));
            if (variable == SegmentType.WILDCARD) { xCount++; minimumWidth++; }
            else if (variable == SegmentType.SEPARATOR) sCount++;
            else nCount++;
        }
        flushLiteral(segments, literal);

        if (minimumWidth == 0) throw new JSONException("A rule must contain a literal or x.");
        if (xCount > 1 || sCount > 1 || nCount > 1) {
            throw new JSONException("A rule can use x, s, and n at most once each.");
        }
        if (containsVariable(replacement, 'x') && xCount == 0
                || containsVariable(replacement, 's') && sCount == 0
                || containsVariable(replacement, 'n') && nCount == 0) {
            throw new JSONException("Destination variables must also appear in the source.");
        }
        return new Rule(source, replacement, segments, new ArrayList<>(exceptions));
    }

    private static boolean containsVariable(String value, char variable) {
        return value != null && value.indexOf(variable) >= 0;
    }

    private static void flushLiteral(List<Segment> segments, StringBuilder literal) {
        if (literal.length() == 0) return;
        segments.add(new Segment(SegmentType.LITERAL, literal.toString()));
        literal.setLength(0);
    }

    private static String transformText(String text, CompiledRules compiled, boolean tagChanges) {
        String source = text == null ? "" : text;
        if (source.isEmpty() || compiled.rules.isEmpty()) return source;

        StringBuilder output = new StringBuilder(source.length());
        int copiedUntil = 0;
        for (int index = 0; index < source.length();) {
            List<Rule> indexed = compiled.byFirstCharacter.get(foldCharacter(source.charAt(index)));
            int indexedIndex = 0;
            int flexibleIndex = 0;
            Rule selectedRule = null;
            Match selectedMatch = null;

            while ((indexed != null && indexedIndex < indexed.size())
                    || flexibleIndex < compiled.flexibleRules.size()) {
                Rule rule;
                if (indexed == null || indexedIndex >= indexed.size()) {
                    rule = compiled.flexibleRules.get(flexibleIndex++);
                } else if (flexibleIndex >= compiled.flexibleRules.size()) {
                    rule = indexed.get(indexedIndex++);
                } else if (indexed.get(indexedIndex).priority >= compiled.flexibleRules.get(flexibleIndex).priority) {
                    rule = indexed.get(indexedIndex++);
                } else {
                    rule = compiled.flexibleRules.get(flexibleIndex++);
                }

                if (!hasWordBoundary(source, index)) continue;
                Match candidate = matchSegments(source, index, rule, 0, new Captures());
                if (candidate != null && hasWordBoundary(source, candidate.end)
                        && !isExactException(source, index, candidate.end, rule.exceptions)
                        && !hasAdjacentException(source, index, candidate.end, rule.exceptions)) {
                    selectedRule = rule;
                    selectedMatch = candidate;
                    break;
                }
            }

            if (selectedRule == null) {
                index++;
                continue;
            }
            output.append(source, copiedUntil, index);
            String replacement = expand(selectedRule.replacement, selectedMatch.captures);
            if (tagChanges && !replacement.contentEquals(source.subSequence(index, selectedMatch.end))) {
                output.append('{').append(source, index, selectedMatch.end).append('}');
            }
            output.append(replacement);
            copiedUntil = selectedMatch.end;
            index = selectedMatch.end;
        }
        output.append(source, copiedUntil, source.length());
        return output.toString();
    }

    private static Match matchSegments(String text, int textIndex, Rule rule, int segmentIndex,
                                       Captures captures) {
        if (segmentIndex >= rule.segments.size()) return new Match(textIndex, captures);
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
                    Match match = matchSegments(text, textIndex + 1, rule, segmentIndex + 1, withNegation);
                    if (match != null) return match;
                }
                return matchSegments(text, textIndex, rule, segmentIndex + 1, captures);
            }
            case SEPARATOR: {
                int end = textIndex;
                while (end < text.length()) {
                    int codePoint = text.codePointAt(end);
                    if (!isSeparator(codePoint)) break;
                    end += Character.charCount(codePoint);
                }
                Captures withSeparator = captures.copy();
                withSeparator.separator = text.substring(textIndex, end);
                return matchSegments(text, end, rule, segmentIndex + 1, withSeparator);
            }
            case WILDCARD: {
                int end = textIndex;
                boolean hasArabicLetter = false;
                ArrayList<Integer> possibleEnds = new ArrayList<>();
                while (end < text.length()) {
                    int codePoint = text.codePointAt(end);
                    if (!isWildcardCharacter(codePoint)) break;
                    if (isArabicLetter(codePoint)) hasArabicLetter = true;
                    end += Character.charCount(codePoint);
                    if (hasArabicLetter) possibleEnds.add(end);
                }
                for (int i = possibleEnds.size() - 1; i >= 0; i--) {
                    int candidateEnd = possibleEnds.get(i);
                    Captures withWildcard = captures.copy();
                    withWildcard.wildcard = text.substring(textIndex, candidateEnd);
                    Match match = matchSegments(text, candidateEnd, rule, segmentIndex + 1, withWildcard);
                    if (match != null) return match;
                }
                return null;
            }
            default: return null;
        }
    }

    private static int matchLiteralEnd(String text, int start, String literal) {
        if (start + literal.length() > text.length()) return -1;
        for (int i = 0; i < literal.length(); i++) {
            if (foldCharacter(text.charAt(start + i)) != foldCharacter(literal.charAt(i))) return -1;
        }
        return start + literal.length();
    }

    private static String expand(String replacement, Captures captures) {
        StringBuilder result = new StringBuilder(replacement.length() + 8);
        for (int i = 0; i < replacement.length(); i++) {
            char character = replacement.charAt(i);
            if (character == 'x') result.append(captures.wildcard == null ? "" : captures.wildcard);
            else if (character == 's') result.append(captures.separator);
            else if (character == 'n') result.append(captures.negation);
            else result.append(character);
        }
        return result.toString();
    }

    private static boolean isExactException(String text, int start, int end, List<String> exceptions) {
        for (String exception : exceptions) if (matchLiteralEnd(text, start, exception) == end) return true;
        return false;
    }

    private static boolean hasAdjacentException(String text, int start, int end, List<String> exceptions) {
        for (String exception : exceptions) {
            int after = end;
            while (after < text.length()) {
                int codePoint = text.codePointAt(after);
                if (!isSeparator(codePoint)) break;
                after += Character.charCount(codePoint);
            }
            int afterEnd = matchLiteralEnd(text, after, exception);
            if (afterEnd >= 0 && hasWordBoundary(text, after) && hasWordBoundary(text, afterEnd)) return true;

            int before = start;
            while (before > 0) {
                int codePoint = text.codePointBefore(before);
                if (!isSeparator(codePoint)) break;
                before -= Character.charCount(codePoint);
            }
            int beforeStart = before - exception.length();
            if (beforeStart >= 0 && matchLiteralEnd(text, beforeStart, exception) == before
                    && hasWordBoundary(text, beforeStart) && hasWordBoundary(text, before)) return true;
        }
        return false;
    }

    private static boolean hasWordBoundary(String text, int index) {
        boolean before = index > 0 && isWordCodePoint(text.codePointBefore(index));
        boolean after = index < text.length() && isWordCodePoint(text.codePointAt(index));
        return before != after;
    }

    private static boolean isWordCodePoint(int codePoint) {
        if (codePoint == '_' || codePoint == 0x200c) return true;
        int type = Character.getType(codePoint);
        return type == Character.UPPERCASE_LETTER || type == Character.LOWERCASE_LETTER
                || type == Character.TITLECASE_LETTER || type == Character.MODIFIER_LETTER
                || type == Character.OTHER_LETTER || type == Character.DECIMAL_DIGIT_NUMBER
                || type == Character.LETTER_NUMBER || type == Character.OTHER_NUMBER;
    }

    private static boolean isSeparator(int codePoint) {
        return codePoint == 0x200c || Character.isWhitespace(codePoint);
    }

    private static boolean isWildcardCharacter(int codePoint) {
        return codePoint == 0x200c || isArabicLetter(codePoint) || isArabicMark(codePoint);
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

    private static char foldCharacter(char character) {
        return character == '\u0622' ? '\u0627' : character;
    }

    private static final class CompiledRules {
        final List<Rule> rules;
        final Map<Character, List<Rule>> byFirstCharacter = new HashMap<>();
        final List<Rule> flexibleRules = new ArrayList<>();

        CompiledRules(List<Rule> rules) {
            this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
            for (Rule rule : rules) {
                Segment first = rule.segments.get(0);
                if (first.type == SegmentType.LITERAL && !first.literal.isEmpty()) {
                    byFirstCharacter.computeIfAbsent(foldCharacter(first.literal.charAt(0)),
                            ignored -> new ArrayList<>()).add(rule);
                    continue;
                }
                if (first.type == SegmentType.NEGATION) {
                    byFirstCharacter.computeIfAbsent('\u0646', ignored -> new ArrayList<>()).add(rule);
                }
                flexibleRules.add(rule);
            }
            for (List<Rule> values : byFirstCharacter.values()) values.sort((a, b) -> Integer.compare(b.priority, a.priority));
            flexibleRules.sort((a, b) -> Integer.compare(b.priority, a.priority));
        }
    }

    private enum SegmentType { LITERAL, WILDCARD, NEGATION, SEPARATOR }

    private static final class Segment {
        final SegmentType type;
        final String literal;
        Segment(SegmentType type, String literal) { this.type = type; this.literal = literal; }
    }

    private static final class Rule {
        final String source;
        final String replacement;
        final List<Segment> segments;
        final List<String> exceptions;
        final int priority;
        Rule(String source, String replacement, List<Segment> segments, List<String> exceptions) {
            this.source = source;
            this.replacement = replacement;
            this.segments = segments;
            this.exceptions = exceptions;
            this.priority = source.length();
        }
    }

    private static final class Captures {
        String wildcard;
        String separator = "";
        String negation = "";
        Captures copy() {
            Captures copy = new Captures();
            copy.wildcard = wildcard;
            copy.separator = separator;
            copy.negation = negation;
            return copy;
        }
    }

    private static final class Match {
        final int end;
        final Captures captures;
        Match(int end, Captures captures) { this.end = end; this.captures = captures; }
    }
}
