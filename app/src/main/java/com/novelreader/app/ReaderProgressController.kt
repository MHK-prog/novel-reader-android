package com.novelreader.app

import android.view.View
import org.json.JSONObject

/** Persists reading position and updates chapter progress from the reader viewport. */
internal class ReaderProgressController(private val host: MainActivity) {
    fun persistPosition(commit: Boolean) {
        val book = host.activeBook() ?: return
        val chapter = host.activeChapterObject() ?: return
        val scroll = host.readerScroll ?: return
        val store = host.readingPositionStore ?: return
        val viewportHeight = scroll.height
        val scrollRange = (host.readerContentHeight() - viewportHeight).coerceAtLeast(0)
        store.savePosition(
            book.optString("id"), book.optString("collectionId", "other"),
            ChapterCatalog.storageIndex(chapter, host.activeChapter), chapter, scroll.scrollY,
            viewportHeight, scrollRange, commit,
        )
    }

    fun restorePosition(chapter: JSONObject?, scrollRange: Int): Int {
        val store = host.readingPositionStore ?: return 0
        if (chapter == null) return 0
        val savedState = host.readerRestoreState ?: chapter
        val restored = store.restoreScroll(
            host.activeBookId,
            ChapterCatalog.storageIndex(chapter, host.activeChapter), savedState,
            host.readerScroll?.height ?: 0, scrollRange,
        )
        host.readerRestoreState = null
        return restored
    }

    fun scheduleProgressSave() {
        host.progressRunnable?.let { host.handler.removeCallbacks(it) }
        val save = Runnable {
            if (host.page == "reader") {
                host.rememberRecentReading()
                host.saveLibrary()
            }
        }
        host.progressRunnable = save
        host.handler.postDelayed(save, 900)
    }

    fun updateProgress() {
        if (host.chapterEditor == null || host.readerScroll == null || host.readerProgress == null) return
        val chapter = host.activeChapterObject() ?: return
        val finishedWriting = host.chapterEnd
        if (chapter.optBoolean("end", false) != finishedWriting) {
            host.putJson(chapter, "end", finishedWriting)
        }
        if (!finishedWriting && host.page == "reader") stopReadingTimer()

        val scroll = host.readerScroll ?: return
        val viewportHeight = scroll.height
        val range = (host.readerContentHeight() - viewportHeight).coerceAtLeast(0)
        val hasText = host.chapterText.trim().isNotEmpty()
        val rawPercent = if (range == 0) 100f else scroll.scrollY * 100f / range
        var percent = if (!finishedWriting || !hasText) 0
        else Math.round(rawPercent).coerceIn(0, 100)
        if (finishedWriting && hasText && rawPercent > 97f) percent = 100

        host.putJson(chapter, "percent", percent)
        host.putJson(chapter, "done", finishedWriting && hasText && percent >= 100)
        host.putJson(chapter, "manualDone", false)
        host.putJson(chapter, "scroll", scroll.scrollY)
        host.putJson(chapter, "scrollRange", range)
        host.putJson(chapter, "scrollViewportHeight", viewportHeight)
        host.putJson(chapter, "scrollFraction", if (range == 0) 0f else scroll.scrollY / range.toFloat())

        val progress = host.readerProgress ?: return
        val width = (progress.parent as? View)?.width ?: 0
        val progressWidth = Math.round(width * percent / 100f)
        if (progressWidth != host.lastReaderProgressWidth) {
            progress.layoutParams = progress.layoutParams.apply { this.width = progressWidth }
            host.lastReaderProgressWidth = progressWidth
        }
        if (host.chapterAdapter != null && host.page == "chapters") {
            host.chapterAdapter.notifyDataSetChanged()
        }
    }

    fun startReadingTimer() {
        host.readingSessionTimer?.markActivity(host.page == "reader" && host.chapterEnd)
    }

    fun stopReadingTimer() {
        host.readingSessionTimer?.stop()
    }

    fun markReadingActivity() {
        startReadingTimer()
    }
}
