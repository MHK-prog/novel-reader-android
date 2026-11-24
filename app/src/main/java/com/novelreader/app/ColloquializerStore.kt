package com.novelreader.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/** Stores the optional replacement rules used by the chapter reader. */
class ColloquializerStore(
    private val storage: NovelStorage,
    private val preferences: SharedPreferences,
) {
    fun isInstalled(): Boolean = try {
        storage.readPluginFile(STORAGE_ID, RULES_FILE) != null
    } catch (_: Exception) {
        false
    }

    fun isEnabled(): Boolean = preferences.getBoolean(ENABLED_KEY, false)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(ENABLED_KEY, enabled).apply()
    }

    @Throws(Exception::class)
    fun readRules(): String = storage.readPluginFile(STORAGE_ID, RULES_FILE)
        ?: throw IOException("Colloquializer rules are missing.")

    @Throws(Exception::class)
    fun saveRules(rulesJson: String) {
        validateRules(rulesJson)
        if (!storage.writePluginFile(STORAGE_ID, RULES_FILE, rulesJson)) {
            throw IOException(storage.lastError())
        }
        storage.keepColloquializerRulesOnly()
        setEnabled(true)
    }

    @Throws(Exception::class)
    fun transform(input: String): String = TextReplacementEngine.transform(input, readRules())

    @Throws(JSONException::class, IOException::class)
    private fun validateRules(rulesJson: String) {
        val pairs = JSONArray(rulesJson)
        if (pairs.length() > MAX_RULES) throw IOException("Extension rule count is too large.")
        for (i in 0 until pairs.length()) {
            val pair = pairs.optJSONArray(i)
            if (pair == null || pair.length() !in 2..3 ||
                pair.opt(0) !is String || pair.opt(1) !is String || pair.optString(0).isEmpty()
            ) {
                throw IOException("Each text rule must contain source and replacement strings.")
            }
            if (pair.length() == 3) {
                val exceptions = pair.optJSONArray(2)
                    ?: throw IOException("Text rule exceptions must be an array.")
                for (j in 0 until exceptions.length()) {
                    val exception = exceptions.opt(j)
                    if (exception !is String || exception.trim().isEmpty()) {
                        throw IOException("Text rule exceptions must be non-empty strings.")
                    }
                }
            }
        }
        TextReplacementEngine.transform("", pairs.toString())
    }

    companion object {
        private const val STORAGE_ID = "colloquializer"
        private const val RULES_FILE = "rules.json"
        private const val ENABLED_KEY = "enabled.colloquializer"
        private const val MAX_RULES = 10_000
    }
}
