package com.novelreader.app

import android.app.AlertDialog
import android.graphics.Color
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ListView
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayList
import java.util.HashSet

/** Builds the Chapter list and owns its selection and editing actions. */
internal class ChapterListScreen(private val host: MainActivity) {
    fun show() {
        val book = host.activeBook()
        if (book == null) {
            host.showDashboard()
            return
        }

        host.setPage("chapters")
        host.appColumn.removeAllViews()
        host.animatePageTransition()
        addHeader(book)
        if (host.selectedChapterKeys.isEmpty()) {
            host.titleView?.let { title ->
                title.contentDescription = "کپی نام کتاب و نویسنده"
                title.setOnClickListener { host.copyBookCredits(book) }
            }
            host.subtitleView?.let { subtitle ->
                subtitle.contentDescription = "کپی نام کتاب و نویسنده"
                subtitle.setOnClickListener { host.copyBookCredits(book) }
            }
        }

        val list = ListView(host).apply {
            divider = null
            cacheColorHint = Color.TRANSPARENT
            setBackgroundColor(host.BG)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            setPadding(host.dp(12f), host.dp(6f), host.dp(12f), host.dp(6f))
            clipToPadding = false
        }
        host.chaptersList = list
        val adapter = ChapterCardAdapter(host)
        host.chapterAdapter = adapter
        list.adapter = adapter
        host.appColumn.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        val nextUnread = ChapterCatalog.firstUnread(book)
        list.post {
            list.setSelectionFromTop(
                if (nextUnread < 0) 0 else nextUnread,
                maxOf(host.dp(48f), list.height / 3)
            )
        }
    }

    private fun addHeader(book: JSONObject) {
        val header: View = when {
            host.selectedChapterKeys.isEmpty() -> host.makeHeader(
                host.displayBookTitle(book), book.optString("author"),
                "add", null, Runnable { addChapter() }, null
            )
            host.selectedChapterKeys.size == 1 -> host.makeHeader(
                "1 Chapter selected", "", "edit", "delete",
                Runnable { renameSelectedChapter() },
                Runnable { confirmDeleteSelectedChapters() }, 0, 118
            )
            else -> host.makeHeader(
                host.selectedChapterKeys.size.toString() + " Chapters selected", "",
                "delete", null, Runnable { confirmDeleteSelectedChapters() }, null
            )
        }
        host.appColumn.addView(header, 0, LinearLayout.LayoutParams(-1, host.dp(62f)))
    }

    fun confirmDeleteSelectedChapters() {
        val book = host.activeBook() ?: return
        val chapters = book.optJSONArray("chapters") ?: return
        if (host.selectedChapterKeys.isEmpty()) return

        val selected = HashSet(host.selectedChapterKeys)
        var count = 0
        for (index in 0 until chapters.length()) {
            val chapter = chapters.optJSONObject(index) ?: continue
            if (selected.contains(host.chapterSelectionKey(chapter, index))) count++
        }
        if (count == 0) {
            host.selectedChapterKeys.clear()
            refreshSelection()
            return
        }

        val message = if (count == 1) "Delete the selected Chapter?"
        else "Delete " + count + " selected Chapters?"
        val dialog = AlertDialog.Builder(host)
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val before = host.library.toString()
                val bookFolder = book.optString("id", "")
                val chapterFiles = ArrayList<String>()
                val originalFiles = ArrayList<String>()
                val kept = JSONArray()

                for (index in 0 until chapters.length()) {
                    val chapter = chapters.optJSONObject(index)
                    if (chapter == null || !selected.contains(host.chapterSelectionKey(chapter, index))) {
                        kept.put(chapter)
                        continue
                    }
                    val chapterId = chapter.optString("id", "")
                    if (chapterId.isNotEmpty()) {
                        chapterFiles.add(chapterId + ".txt")
                        originalFiles.add(host.chapterOriginalFileName(chapter))
                    }
                }

                for (index in 0 until kept.length()) host.putJson(kept.optJSONObject(index), "orderIndex", index)
                host.putJson(book, "chapters", kept)
                if (!host.saveLibrary()) {
                    host.restoreLibrary(before)
                    dialog.dismiss()
                    host.toast("Could not delete the selected Chapters.")
                    return@setOnClickListener
                }

                host.selectedChapterKeys.clear()
                dialog.dismiss()
                var filesDeleted = true
                for (file in chapterFiles) {
                    if (!host.storage.deleteChapterFile(bookFolder, file)) filesDeleted = false
                }
                for (file in originalFiles) {
                    if (!host.storage.deleteChapterOriginal(file)) filesDeleted = false
                }
                refreshSelection()
                if (!filesDeleted) host.toast("Chapters removed; some unused files could not be deleted.")
            }
        }
        dialog.show()
        host.styleDialogWindow(dialog)
    }

    fun refreshSelection() {
        if (host.page != "chapters" || host.appColumn == null) return
        val book = host.activeBook() ?: return
        if (host.appColumn.childCount > 0) host.appColumn.removeViewAt(0)
        addHeader(book)
        host.chapterAdapter?.notifyDataSetChanged()
    }

    fun renameSelectedChapter() {
        if (host.selectedChapterKeys.size != 1) return
        val book = host.activeBook() ?: return
        val chapters = book.optJSONArray("chapters") ?: return
        val selectedKey = host.selectedChapterKeys.first()
        var selected: JSONObject? = null
        var selectedPosition = -1
        for (index in 0 until chapters.length()) {
            val chapter = chapters.optJSONObject(index) ?: continue
            if (selectedKey == host.chapterSelectionKey(chapter, index)) {
                selected = chapter
                selectedPosition = index
                break
            }
        }
        val chapterToEdit = selected ?: return
        val before = host.library.toString()
        val title = host.edit("Chapter title", false).apply {
            setText(ChapterCatalog.label(chapterToEdit, selectedPosition))
            setSelection(length())
        }
        val dialog = AlertDialog.Builder(host)
            .setTitle("Edit Chapter")
            .setView(host.dialogPadding(title))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newTitle = title.text.toString().trim()
                if (newTitle.isEmpty()) {
                    title.error = "Enter a Chapter title"
                    return@setOnClickListener
                }
                host.putJson(chapterToEdit, "title", newTitle)
                if (!host.saveLibrary()) host.restoreLibrary(before)
                dialog.dismiss()
                refreshSelection()
            }
        }
        dialog.show()
        host.styleDialogWindow(dialog)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    fun addChapter() {
        val book = host.activeBook() ?: return
        val chapters = book.optJSONArray("chapters") ?: return
        if (chapters.length() >= 5000) {
            host.toast("حداکثر ۵۰۰۰ Chapter پشتیبانی می‌شود.")
            return
        }
        val bookId = book.optString("id")
        val newIndex = chapters.length()
        val chapterName = host.edit("Chapter name or number", false).apply {
            setText("Chapter " + (newIndex + 1))
            setSelection(length())
        }
        val dialog = AlertDialog.Builder(host)
            .setTitle("Add Chapter")
            .setView(host.dialogPadding(chapterName))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val label = chapterName.text.toString().trim()
                if (label.isEmpty()) {
                    chapterName.error = "Enter a Chapter name or number"
                    return@setOnClickListener
                }
                val currentBook = host.findBookById(bookId) ?: return@setOnClickListener
                val currentChapters = currentBook.optJSONArray("chapters") ?: return@setOnClickListener
                if (currentChapters.length() >= 5000) return@setOnClickListener
                val before = host.library.toString()
                ChapterCatalog.ensureStorageIndexes(currentChapters)
                val chapter = ChapterCatalog.newChapter()
                host.putJson(chapter, "title", label)
                host.putJson(chapter, "storageIndex", currentChapters.length() + 1)
                val insertAt = ChapterCatalog.insertionIndex(currentChapters, label)
                val ordered = JSONArray()
                for (index in 0..currentChapters.length()) {
                    if (index == insertAt) ordered.put(chapter)
                    if (index < currentChapters.length()) ordered.put(currentChapters.optJSONObject(index))
                }
                for (index in 0 until ordered.length()) host.putJson(ordered.optJSONObject(index), "orderIndex", index)
                host.putJson(currentBook, "chapters", ordered)
                if (!host.saveLibrary()) {
                    host.restoreLibrary(before)
                    return@setOnClickListener
                }
                dialog.dismiss()
                host.openReader(insertAt)
            }
        }
        dialog.show()
        host.styleDialogWindow(dialog)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}
