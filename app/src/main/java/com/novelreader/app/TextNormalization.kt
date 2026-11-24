package com.novelreader.app

import java.util.Locale

object TextNormalization {
    @JvmStatic
    fun titleCaseWords(value: String?): String {
        val words = value?.trim()?.lowercase(Locale.ROOT)?.split(Regex("\\s+")) ?: emptyList()
        val result = StringBuilder()
        for (word in words) {
            if (word.isEmpty()) continue
            if (result.isNotEmpty()) result.append(' ')
            var index = 0
            while (index < word.length) {
                val codePoint = word.codePointAt(index)
                if (Character.isLetter(codePoint)) {
                    result.append(word.substring(0, index))
                    result.append(String(Character.toChars(codePoint)).uppercase(Locale.ROOT))
                    result.append(word.substring(index + Character.charCount(codePoint)))
                    break
                }
                index += Character.charCount(codePoint)
            }
            if (index >= word.length) result.append(word)
        }
        return result.toString()
    }
}
