package com.novelreader.app

import org.json.JSONArray
import org.json.JSONException
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap

/** Small literal replacement engine with the x, s, and n variables used by the colloquializer. */
object TextReplacementEngine {
    private var cachedJson: String? = null
    private var cachedRules: CompiledRules? = null

    @JvmStatic
    @Throws(JSONException::class)
    fun transform(text: String?, rulesJson: String?): String =
        transformText(text, loadRules(rulesJson))

    @Synchronized
    @Throws(JSONException::class)
    private fun loadRules(json: String?): CompiledRules {
        val sourceJson = json ?: "[]"
        if (sourceJson == cachedJson && cachedRules != null) return cachedRules!!

        val input = JSONArray(sourceJson)
        val rules = ArrayList<Rule>(input.length())
        for (i in 0 until input.length()) {
            val pair = input.optJSONArray(i)
            if (pair == null || pair.length() < 2 || pair.length() > 3 ||
                pair.opt(0) !is String || pair.opt(1) !is String
            ) {
                throw JSONException("Each rule must contain source, destination, and optional exceptions.")
            }
            val source = pair.optString(0)
            val replacement = pair.optString(1)
            if (source.trim().isEmpty()) throw JSONException("Rule source cannot be empty.")

            val exceptions = ArrayList<String>()
            val exceptionList = pair.optJSONArray(2)
            if (pair.length() == 3 && exceptionList == null) {
                throw JSONException("Rule exceptions must be an array of strings.")
            }
            if (exceptionList != null) {
                for (j in 0 until exceptionList.length()) {
                    val item = exceptionList.opt(j)
                    if (item !is String || item.trim().isEmpty()) {
                        throw JSONException("Rule exceptions must be non-empty strings.")
                    }
                    exceptions.add(item.trim())
                }
            }
            rules.add(compileRule(source, replacement, exceptions))
        }
        rules.sortWith { left, right -> right.priority.compareTo(left.priority) }
        val compiled = CompiledRules(rules)
        cachedRules = compiled
        cachedJson = sourceJson
        return compiled
    }

    @Throws(JSONException::class)
    private fun compileRule(source: String, replacement: String, exceptions: List<String>): Rule {
        val segments = ArrayList<Segment>()
        val literal = StringBuilder()
        var xCount = 0
        var sCount = 0
        var nCount = 0
        var minimumWidth = 0

        for (character in source) {
            val variable = when (character) {
                'x' -> SegmentType.WILDCARD
                's' -> SegmentType.SEPARATOR
                'n' -> SegmentType.NEGATION
                else -> null
            }
            if (variable == null) {
                literal.append(character)
                minimumWidth++
                continue
            }
            flushLiteral(segments, literal)
            segments.add(Segment(variable, null))
            when (variable) {
                SegmentType.WILDCARD -> { xCount++; minimumWidth++ }
                SegmentType.SEPARATOR -> sCount++
                SegmentType.NEGATION -> nCount++
                SegmentType.LITERAL -> Unit
            }
        }
        flushLiteral(segments, literal)

        if (minimumWidth == 0) throw JSONException("A rule must contain a literal or x.")
        if (xCount > 1 || sCount > 1 || nCount > 1) {
            throw JSONException("A rule can use x, s, and n at most once each.")
        }
        if ((containsVariable(replacement, 'x') && xCount == 0) ||
            (containsVariable(replacement, 's') && sCount == 0) ||
            (containsVariable(replacement, 'n') && nCount == 0)
        ) {
            throw JSONException("Destination variables must also appear in the source.")
        }
        return Rule(source, replacement, segments, ArrayList(exceptions))
    }

    private fun containsVariable(value: String?, variable: Char): Boolean =
        value?.contains(variable) == true

    private fun flushLiteral(segments: MutableList<Segment>, literal: StringBuilder) {
        if (literal.isEmpty()) return
        segments.add(Segment(SegmentType.LITERAL, literal.toString()))
        literal.setLength(0)
    }

    private fun transformText(text: String?, compiled: CompiledRules): String {
        val source = text ?: ""
        if (source.isEmpty() || compiled.rules.isEmpty()) return source

        val output = StringBuilder(source.length)
        var copiedUntil = 0
        var index = 0
        while (index < source.length) {
            val indexed = compiled.byFirstCharacter[foldCharacter(source[index])]
            var indexedIndex = 0
            var flexibleIndex = 0
            var selectedRule: Rule? = null
            var selectedMatch: Match? = null

            while ((indexed != null && indexedIndex < indexed.size) ||
                flexibleIndex < compiled.flexibleRules.size
            ) {
                val rule = when {
                    indexed == null || indexedIndex >= indexed.size ->
                        compiled.flexibleRules[flexibleIndex++]
                    flexibleIndex >= compiled.flexibleRules.size -> indexed[indexedIndex++]
                    indexed[indexedIndex].priority >= compiled.flexibleRules[flexibleIndex].priority ->
                        indexed[indexedIndex++]
                    else -> compiled.flexibleRules[flexibleIndex++]
                }

                if (!hasWordBoundary(source, index)) continue
                val candidate = matchSegments(source, index, rule, 0, Captures())
                if (candidate != null && hasWordBoundary(source, candidate.end) &&
                    !isExactException(source, index, candidate.end, rule.exceptions) &&
                    !hasAdjacentException(source, index, candidate.end, rule.exceptions)
                ) {
                    selectedRule = rule
                    selectedMatch = candidate
                    break
                }
            }

            val rule = selectedRule
            val match = selectedMatch
            if (rule == null || match == null) {
                index++
                continue
            }
            output.append(source, copiedUntil, index)
            output.append(expand(rule.replacement, match.captures))
            copiedUntil = match.end
            index = match.end
        }
        output.append(source, copiedUntil, source.length)
        return output.toString()
    }

    private fun matchSegments(
        text: String,
        textIndex: Int,
        rule: Rule,
        segmentIndex: Int,
        captures: Captures,
    ): Match? {
        if (segmentIndex >= rule.segments.size) return Match(textIndex, captures)
        val segment = rule.segments[segmentIndex]
        when (segment.type) {
            SegmentType.LITERAL -> {
                val end = matchLiteralEnd(text, textIndex, segment.literal!!)
                return if (end < 0) null else
                    matchSegments(text, end, rule, segmentIndex + 1, captures)
            }
            SegmentType.NEGATION -> {
                if (textIndex < text.length) {
                    val optionalPrefix = text[textIndex]
                    if (optionalPrefix == '\u0646' || optionalPrefix == '\u0628') {
                        val withPrefix = captures.copy().also { it.negation = optionalPrefix.toString() }
                        val match = matchSegments(text, textIndex + 1, rule, segmentIndex + 1, withPrefix)
                        if (match != null) return match
                    }
                }
                return matchSegments(text, textIndex, rule, segmentIndex + 1, captures)
            }
            SegmentType.SEPARATOR -> {
                var end = textIndex
                while (end < text.length) {
                    val codePoint = text.codePointAt(end)
                    if (!isSeparator(codePoint)) break
                    end += Character.charCount(codePoint)
                }
                val withSeparator = captures.copy().also {
                    it.separator = text.substring(textIndex, end)
                }
                return matchSegments(text, end, rule, segmentIndex + 1, withSeparator)
            }
            SegmentType.WILDCARD -> {
                var end = textIndex
                var hasArabicLetter = false
                val possibleEnds = ArrayList<Int>()
                while (end < text.length) {
                    val codePoint = text.codePointAt(end)
                    if (!isWildcardCharacter(codePoint)) break
                    if (isArabicLetter(codePoint)) hasArabicLetter = true
                    end += Character.charCount(codePoint)
                    if (hasArabicLetter) possibleEnds.add(end)
                }
                for (i in possibleEnds.indices.reversed()) {
                    val candidateEnd = possibleEnds[i]
                    val withWildcard = captures.copy().also {
                        it.wildcard = text.substring(textIndex, candidateEnd)
                    }
                    val match = matchSegments(text, candidateEnd, rule, segmentIndex + 1, withWildcard)
                    if (match != null) return match
                }
                return null
            }
        }
    }

    private fun matchLiteralEnd(text: String, start: Int, literal: String): Int {
        if (start + literal.length > text.length) return -1
        for (i in literal.indices) {
            if (foldCharacter(text[start + i]) != foldCharacter(literal[i])) return -1
        }
        return start + literal.length
    }

    private fun expand(replacement: String, captures: Captures): String {
        val result = StringBuilder(replacement.length + 8)
        for (character in replacement) {
            when (character) {
                'x' -> result.append(captures.wildcard ?: "")
                's' -> result.append(captures.separator)
                'n' -> result.append(captures.negation)
                else -> result.append(character)
            }
        }
        return result.toString()
    }

    private fun isExactException(text: String, start: Int, end: Int, exceptions: List<String>): Boolean =
        exceptions.any { matchLiteralEnd(text, start, it) == end }

    private fun hasAdjacentException(text: String, start: Int, end: Int, exceptions: List<String>): Boolean {
        for (exception in exceptions) {
            var after = end
            while (after < text.length) {
                val codePoint = text.codePointAt(after)
                if (!isSeparator(codePoint)) break
                after += Character.charCount(codePoint)
            }
            val afterEnd = matchLiteralEnd(text, after, exception)
            if (afterEnd >= 0 && hasWordBoundary(text, after) && hasWordBoundary(text, afterEnd)) return true

            var before = start
            while (before > 0) {
                val codePoint = text.codePointBefore(before)
                if (!isSeparator(codePoint)) break
                before -= Character.charCount(codePoint)
            }
            val beforeStart = before - exception.length
            if (beforeStart >= 0 && matchLiteralEnd(text, beforeStart, exception) == before &&
                hasWordBoundary(text, beforeStart) && hasWordBoundary(text, before)
            ) return true
        }
        return false
    }

    private fun hasWordBoundary(text: String, index: Int): Boolean {
        val before = index > 0 && isWordCodePoint(text.codePointBefore(index))
        val after = index < text.length && isWordCodePoint(text.codePointAt(index))
        return before != after
    }

    private fun isWordCodePoint(codePoint: Int): Boolean {
        if (codePoint == '_'.code || codePoint == 0x200c) return true
        return when (Character.getType(codePoint)) {
            Character.UPPERCASE_LETTER.toInt(), Character.LOWERCASE_LETTER.toInt(),
            Character.TITLECASE_LETTER.toInt(), Character.MODIFIER_LETTER.toInt(),
            Character.OTHER_LETTER.toInt(), Character.DECIMAL_DIGIT_NUMBER.toInt(),
            Character.LETTER_NUMBER.toInt(), Character.OTHER_NUMBER.toInt() -> true
            else -> false
        }
    }

    private fun isSeparator(codePoint: Int): Boolean = codePoint == 0x200c || Character.isWhitespace(codePoint)

    private fun isWildcardCharacter(codePoint: Int): Boolean =
        codePoint == 0x200c || isArabicLetter(codePoint) || isArabicMark(codePoint)

    private fun isArabicLetter(codePoint: Int): Boolean =
        Character.isLetter(codePoint) && Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.ARABIC

    private fun isArabicMark(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return (type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt()) &&
            Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.ARABIC
    }

    private fun foldCharacter(character: Char): Char = if (character == '\u0622') '\u0627' else character

    private class CompiledRules(rules: List<Rule>) {
        val rules: List<Rule> = Collections.unmodifiableList(ArrayList(rules))
        val byFirstCharacter = HashMap<Char, MutableList<Rule>>()
        val flexibleRules = ArrayList<Rule>()

        init {
            for (rule in rules) {
                val first = rule.segments.first()
                if (first.type == SegmentType.LITERAL && !first.literal.isNullOrEmpty()) {
                    byFirstCharacter.getOrPut(foldCharacter(first.literal[0])) { ArrayList() }.add(rule)
                    continue
                }
                if (first.type == SegmentType.NEGATION) {
                    byFirstCharacter.getOrPut('\u0646') { ArrayList() }.add(rule)
                }
                flexibleRules.add(rule)
            }
            byFirstCharacter.values.forEach { it.sortByDescending(Rule::priority) }
            flexibleRules.sortByDescending(Rule::priority)
        }
    }

    private enum class SegmentType { LITERAL, WILDCARD, NEGATION, SEPARATOR }
    private class Segment(val type: SegmentType, val literal: String?)
    private class Rule(
        val source: String,
        val replacement: String,
        val segments: List<Segment>,
        val exceptions: List<String>,
    ) {
        val priority: Int = source.length
    }

    private class Captures {
        var wildcard: String? = null
        var separator: String = ""
        var negation: String = ""

        fun copy() = Captures().also {
            it.wildcard = wildcard
            it.separator = separator
            it.negation = negation
        }
    }

    private class Match(val end: Int, val captures: Captures)
}
