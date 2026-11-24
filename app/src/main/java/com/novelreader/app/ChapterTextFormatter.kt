package com.novelreader.app

/** Small plain-text helpers for Chapters. */
object ChapterTextFormatter {
    @JvmStatic
    fun countWords(value: String?): Long {
        if (value.isNullOrEmpty()) return 0

        var count = 0L
        var inWord = false
        var offset = 0
        while (offset < value.length) {
            val codePoint = value.codePointAt(offset)
            val wordCharacter = Character.isLetterOrDigit(codePoint) || codePoint == 0x200C
            if (wordCharacter && !inWord) count++
            inWord = wordCharacter
            offset += Character.charCount(codePoint)
        }
        return count
    }
}
