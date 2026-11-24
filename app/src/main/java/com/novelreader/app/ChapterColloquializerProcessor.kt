package com.novelreader.app

import java.io.IOException

/** Performs chapter-file work off the UI thread; the Activity owns progress and rendering. */
class ChapterColloquializerProcessor(
    private val storage: NovelStorage,
    private val colloquializer: ColloquializerStore
) {
    fun process(
        originalFile: String,
        currentText: String,
        restoreOriginal: Boolean,
        alreadyColloquialized: Boolean
    ): Result {
        var source: String? = null
        return try {
            if (restoreOriginal || alreadyColloquialized) {
                source = storage.readChapterOriginal(originalFile)
                    ?: throw IOException("نسخهٔ اصلی این Chapter پیدا نشد.")
            } else {
                source = currentText
                if (!storage.writeChapterOriginal(originalFile, currentText)) {
                    throw IOException(storage.lastError())
                }
            }
            val original = source ?: throw IOException("نسخهٔ اصلی این Chapter پیدا نشد.")
            val output = if (restoreOriginal) original else colloquializer.transform(original)
            Result(original, output, null)
        } catch (error: Exception) {
            Result(source, null, error.message ?: error.javaClass.simpleName)
        }
    }

    class Result(
        @JvmField val source: String?,
        @JvmField val output: String?,
        @JvmField val error: String?
    )
}
