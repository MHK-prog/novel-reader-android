package com.novelreader.app

import android.content.Context
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager

/** Keeps reader preview/edit transitions and saved scroll position together. */
internal class ReaderViewport(private val host: MainActivity) {
    fun renderPreview() {
        val target = host.readerPreview ?: return
        target.text = host.chapterText
        target.typeface = host.vazir
        if (host.restoreReaderAfterRender) {
            host.restoreReaderAfterRender = false
            restorePositionAfterRender()
        } else {
            host.updateProgress()
        }
    }

    fun enterEditMode(showKeyboard: Boolean) {
        val cursor = host.chapterEditor?.selectionStart?.coerceAtLeast(0) ?: 0
        enterEditMode(showKeyboard, cursor)
    }

    fun enterEditMode(showKeyboard: Boolean, cursorOffset: Int) {
        val editor = host.chapterEditor ?: return
        val preview = host.readerPreview ?: return
        if (host.readerEditing) return

        host.hasPendingReaderScrollFraction = false
        host.readerEditing = true
        preview.visibility = View.GONE
        editor.visibility = View.VISIBLE
        if (editor.text.toString() != host.chapterText) editor.setText(host.chapterText)

        val requestedCursor = cursorOffset.coerceIn(0, editor.length())
        host.pendingReaderCursorOffset = requestedCursor
        editor.setSelection(requestedCursor)
        afterLayout(Runnable {
            editor.setSelection(requestedCursor)
            positionCursorInUpperHalf(requestedCursor)
            if (showKeyboard && host.chapterEditor != null) {
                editor.requestFocus()
                editor.post {
                    val input = host.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    input?.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
                    editor.postDelayed({ positionCursorInUpperHalf(requestedCursor) }, 180)
                }
            }
            host.updateProgress()
        })
    }

    fun positionCursorInUpperHalf(cursorOffset: Int) {
        val scroll = host.readerScroll ?: return
        val editor = host.chapterEditor ?: return
        if (!host.readerEditing) return
        val layout = editor.layout ?: return
        if (layout.lineCount == 0 || scroll.height <= 0) return

        val safeOffset = cursorOffset.coerceIn(0, editor.length())
        val line = layout.getLineForOffset(safeOffset).coerceIn(0, layout.lineCount - 1)
        val lineCenter = editor.top + editor.totalPaddingTop +
            (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
        val range = (readerContentHeight() - scroll.height).coerceAtLeast(0)
        val target = (lineCenter - scroll.height * .34f).toInt().coerceIn(0, range)
        scroll.scrollTo(0, target)
    }

    fun previewCursorOffset(touchX: Float, touchY: Float): Int {
        val preview = host.readerPreview ?: return 0
        val chapterText = host.chapterText
        val layout = preview.layout
        val displayed = preview.text
        if (displayed.isEmpty()) return 0
        if (layout == null) {
            val contentHeight = maxOf(preview.height, readerContentHeight())
            if (contentHeight <= 0) return 0
            val scrollY = host.readerScroll?.scrollY ?: 0
            val position = ((scrollY + touchY) / contentHeight).coerceIn(0f, 1f)
            return (chapterText.length * position).toInt().coerceIn(0, chapterText.length)
        }

        val vertical = (touchY.toInt() - preview.totalPaddingTop).coerceAtLeast(0)
        val line = layout.getLineForVertical(vertical).coerceIn(0, layout.lineCount - 1)
        val visibleOffset = layout.getOffsetForHorizontal(line, touchX - preview.totalPaddingLeft)
            .coerceIn(0, displayed.length)
        return visibleOffset.coerceIn(0, chapterText.length)
    }

    fun afterLayout(action: Runnable) {
        val anchor: View? = host.readerContentContainer ?: host.readerScroll
        if (anchor == null) {
            action.run()
            return
        }
        val observer = anchor.viewTreeObserver
        if (!observer.isAlive) {
            anchor.post { anchor.post(action) }
            return
        }

        lateinit var listener: ViewTreeObserver.OnGlobalLayoutListener
        listener = ViewTreeObserver.OnGlobalLayoutListener {
            val scroll = host.readerScroll
            if (anchor.width <= 0 || anchor.height <= 0 || scroll == null || scroll.height <= 0) return@OnGlobalLayoutListener
            val currentObserver = anchor.viewTreeObserver
            if (currentObserver.isAlive) currentObserver.removeOnGlobalLayoutListener(listener)
            anchor.post(action)
        }
        observer.addOnGlobalLayoutListener(listener)
        anchor.requestLayout()
    }

    fun leaveEditMode() {
        val editor = host.chapterEditor ?: return
        val preview = host.readerPreview ?: return
        if (!host.readerEditing || host.chapterText.trim().isEmpty()) return

        host.pendingReaderScrollFraction = scrollFraction()
        host.hasPendingReaderScrollFraction = true
        host.readerEditing = false
        editor.visibility = View.GONE
        preview.visibility = View.VISIBLE
        host.restoreReaderAfterRender = true
        renderPreview()
    }

    fun readerContentHeight(): Int = host.readerContentContainer?.height
        ?: host.chapterEditor?.height ?: 0

    fun scrollFraction(): Float {
        val scroll = host.readerScroll ?: return 0f
        val range = (readerContentHeight() - scroll.height).coerceAtLeast(0)
        return if (range == 0) 0f else (scroll.scrollY.toFloat() / range).coerceIn(0f, 1f)
    }

    private fun restoreScrollFraction() {
        val scroll = host.readerScroll ?: return
        if (!host.hasPendingReaderScrollFraction) return
        val range = (readerContentHeight() - scroll.height).coerceAtLeast(0)
        scroll.scrollTo(0, (range * host.pendingReaderScrollFraction).toInt())
        host.hasPendingReaderScrollFraction = false
    }

    fun restorePositionAfterRender() {
        val scroll = host.readerScroll ?: return
        if (host.hasPendingReaderScrollFraction) {
            afterLayout(Runnable {
                restoreScrollFraction()
                host.restoringScroll = false
                host.updateProgress()
                host.persistCurrentReadingPosition(false)
                host.scheduleProgressSave()
            })
            return
        }

        afterLayout(Runnable {
            val chapter = host.activeChapterObject()
            val range = (readerContentHeight() - scroll.height).coerceAtLeast(0)
            scroll.scrollTo(0, host.restoreSavedReadingPosition(chapter, range))
            host.restoringScroll = false
            host.updateProgress()
            host.persistCurrentReadingPosition(false)
            host.scheduleProgressSave()
        })
    }
}
