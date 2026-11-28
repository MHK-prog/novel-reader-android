package com.novelreader.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Stores app-owned rules and imports the old extension-folder rules once if present. */
class ColloquializerStore(
    private val context: Context,
    private val legacyStorage: NovelStorage,
    private val preferences: SharedPreferences,
) {
    fun isInstalled(): Boolean = ensureRulesFile().isFile

    fun isEnabled(): Boolean = preferences.getBoolean(ENABLED_KEY, false)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(ENABLED_KEY, enabled).apply()
    }

    @Throws(Exception::class)
    fun readRules(): String = ensureRulesFile().readText(Charsets.UTF_8)

    @Throws(Exception::class)
    fun saveRules(rulesJson: String) {
        validateRules(rulesJson)
        val file = ensureRulesFile()
        val temporary = File(file.parentFile, "$RULES_FILE.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(rulesJson.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        if (!temporary.renameTo(file)) {
            temporary.delete()
            throw IOException("Could not save colloquializer rules.")
        }
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

    private fun ensureRulesFile(): File {
        val file = File(context.filesDir, "colloquializer/$RULES_FILE")
        if (file.isFile) return file
        file.parentFile?.mkdirs()
        val legacy = try { legacyStorage.readPluginFile(STORAGE_ID, RULES_FILE) } catch (_: Exception) { null }
        val contents = if (legacy != null) {
            try {
                validateRules(legacy)
                legacy
            } catch (_: Exception) {
                "[]"
            }
        } else "[]"
        file.writeText(contents, Charsets.UTF_8)
        return file
    }

    companion object {
        private const val STORAGE_ID = "colloquializer"
        private const val RULES_FILE = "rules.json"
        private const val ENABLED_KEY = "enabled.colloquializer"
        private const val MAX_RULES = 10_000
    }
}
