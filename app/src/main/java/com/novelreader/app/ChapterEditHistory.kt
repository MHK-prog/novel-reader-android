package com.novelreader.app

import android.os.SystemClock
import java.util.ArrayDeque

/** Bounded undo/redo history with short bursts of typed characters grouped together. */
class ChapterEditHistory {
    private val undo = ArrayDeque<String>()
    private val redo = ArrayDeque<String>()
    private var restoring = false
    private var lastWasTyping = false
    private var lastEditAt = 0L
    private var lastEditEnd = -1

    fun isRestoring(): Boolean = restoring

    fun beginRestore() {
        restoring = true
    }

    fun endRestore() {
        restoring = false
        lastWasTyping = false
        lastEditEnd = -1
    }

    fun recordBeforeChange(text: CharSequence, start: Int, count: Int, after: Int) {
        if (restoring) return
        redo.clear()
        val now = SystemClock.uptimeMillis()
        val typing = count == 0 && after == 1
        val coalesce = typing && lastWasTyping && start == lastEditEnd && now - lastEditAt < 850
        val snapshot = text.toString()
        if (!coalesce && (undo.isEmpty() || undo.peekLast() != snapshot)) push(undo, snapshot)
        lastWasTyping = typing
        lastEditAt = now
        lastEditEnd = start + after
    }

    fun pushUndo(text: String) {
        push(undo, text)
        redo.clear()
        lastWasTyping = false
        lastEditEnd = -1
    }

    fun undo(current: String): String? {
        if (undo.isEmpty()) return null
        push(redo, current)
        lastWasTyping = false
        lastEditEnd = -1
        return undo.removeLast()
    }

    fun redo(current: String): String? {
        if (redo.isEmpty()) return null
        push(undo, current)
        lastWasTyping = false
        lastEditEnd = -1
        return redo.removeLast()
    }

    fun clear() {
        undo.clear()
        redo.clear()
        lastWasTyping = false
        lastEditEnd = -1
    }

    private fun push(history: ArrayDeque<String>, text: String) {
        if (history.peekLast() != text) history.addLast(text)
        while (history.size > MAX_HISTORY) history.removeFirst()
    }

    companion object {
        private const val MAX_HISTORY = 80
    }
}
