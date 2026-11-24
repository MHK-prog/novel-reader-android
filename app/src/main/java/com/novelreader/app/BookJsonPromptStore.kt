package com.novelreader.app

import android.content.Context

/** Loads and stores the editable prompt used for book JSON import. */
class BookJsonPromptStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun defaultPrompt(): String = try {
        context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (_: Exception) {
        ""
    }

    fun load(): String = preferences.getString(PROMPT_KEY, null) ?: defaultPrompt()

    fun save(value: String) {
        preferences.edit().putString(PROMPT_KEY, value).apply()
    }

    fun fillField(prompt: String, label: String, value: String?): String {
        val labelStart = prompt.indexOf(label)
        if (labelStart < 0) return prompt
        val valueStart = labelStart + label.length
        val foundLineEnd = prompt.indexOf('\n', valueStart)
        val lineEnd = if (foundLineEnd < 0) prompt.length else foundLineEnd
        val contentEnd = if (lineEnd > valueStart && prompt[lineEnd - 1] == '\r') lineEnd - 1 else lineEnd
        val cleanValue = value.orEmpty().replace('\r', ' ').replace('\n', ' ').trim()
        return prompt.substring(0, valueStart) +
            (if (cleanValue.isEmpty()) "" else " $cleanValue") + prompt.substring(contentEnd)
    }

    private companion object {
        const val PREFERENCES = "general"
        const val PROMPT_KEY = "book_json_prompt"
        const val ASSET = "book_info_prompt.txt"
    }
}
