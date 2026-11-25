package com.novelreader.app

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Owns actions shown in the chapter reader's options menu. */
internal class ChapterReaderActions(private val host: MainActivity) {
    fun createOptionsButton(): FrameLayout {
        val button = FrameLayout(host)
        host.styleIconButton(button, false)
        button.contentDescription = "گزینه‌های Chapter"
        button.isClickable = true
        button.isFocusable = true
        host.applyPressScale(button)
        button.setOnClickListener { showOptions() }
        host.readerActionButton = button
        host.readerActionIcon = NativeIconView(host, "menu", host.PURPLE)
        button.addView(host.readerActionIcon,
            FrameLayout.LayoutParams(host.dp(24f), host.dp(24f), Gravity.CENTER))
        host.readerActionSpinner = ProgressBar(host).apply {
            isIndeterminate = true
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                indeterminateTintList = android.content.res.ColorStateList.valueOf(host.PURPLE)
            }
            visibility = View.GONE
        }
        button.addView(host.readerActionSpinner,
            FrameLayout.LayoutParams(host.dp(28f), host.dp(28f), Gravity.CENTER))
        return button
    }

    fun showColloquializerDialog() {
        if (host.page != "reader" || host.chapterEditor == null) return
        val choices = ArrayList<String>()
        val actions = ArrayList<Int>()
        choices.add("محاوره‌ای‌سازی Chapter")
        actions.add(0)
        if (host.chapterColloquialized) {
            choices.add("بازگرداندن متن اصلی")
            actions.add(1)
        }
        val dialog = AlertDialog.Builder(host)
            .setTitle("محاوره‌ای‌ساز")
            .setItems(choices.toTypedArray()) { _, which ->
                when (actions[which]) {
                    0 -> runColloquializer(false)
                    1 -> runColloquializer(true)
                }
            }
            .setNegativeButton("لغو", null)
            .create()
        dialog.show()
        host.styleDialogWindow(dialog)
    }

    fun showOptions() {
        if (host.page != "reader" || host.chapterEditor == null) return
        val labels = ArrayList<String>()
        val actions = ArrayList<Runnable>()
        if (host.colloquializerStore != null && host.colloquializerStore.isEnabled()) {
            labels.add("محاوره‌ای‌سازی")
            actions.add(Runnable { showColloquializerDialog() })
        }
        labels.add(if (host.chapterEnd) "لغو پایان نوشتن Chapter" else "اعلام پایان نوشتن Chapter")
        actions.add(Runnable { setChapterEnd(!host.chapterEnd) })
        labels.add("وارد کردن متن از فایل")
        actions.add(Runnable { pickChapterTextFile() })
        val dialog = AlertDialog.Builder(host)
            .setTitle("گزینه‌های Chapter")
            .setItems(labels.toTypedArray()) { _, which -> actions[which].run() }
            .setNegativeButton("بستن", null)
            .create()
        dialog.show()
        host.styleDialogWindow(dialog)
    }

    fun setChapterEnd(finished: Boolean) {
        val chapter = host.activeChapterObject() ?: return
        val editor = host.chapterEditor ?: return
        host.chapterEnd = finished
        host.putJson(chapter, "end", finished)
        host.chapterText = editor.text.toString()
        if (finished) host.calculateAndStoreChapterEstimate(chapter, host.chapterText)
        if (!finished) {
            host.putJson(chapter, "done", false)
            host.putJson(chapter, "percent", 0)
        }
        host.updateProgress()
        if (host.saveCurrentChapter()) {
            host.toast(if (finished) "پایان نوشتن Chapter ثبت شد." else "پایان نوشتن Chapter لغو شد.")
        }
    }

    fun pickChapterTextFile() {
        val chapter = host.activeChapterObject() ?: return
        if (host.page != "reader") return
        host.pendingChapterTextBookId = host.activeBookId
        host.pendingChapterTextChapterId = chapter.optString("id", "")
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
        }
        try {
            host.startActivityForResult(intent, MainActivity.REQUEST_CHAPTER_TEXT)
        } catch (_: Exception) {
            host.toast("انتخاب فایل متنی در دسترس نیست.")
        }
    }

    fun importChapterText(uri: Uri) {
        val targetBookId = host.pendingChapterTextBookId
        val targetChapterId = host.pendingChapterTextChapterId
        host.pendingChapterTextBookId = null
        host.pendingChapterTextChapterId = null
        if (targetBookId == null || targetChapterId == null) return
        host.chapterIoExecutor.execute {
            var imported: String? = null
            var failure: String? = null
            try {
                val input = host.contentResolver.openInputStream(uri) ?: throw IOException("فایل خوانده نشد.")
                input.use { source ->
                    ByteArrayOutputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        imported = output.toString("UTF-8").removePrefix("\uFEFF")
                    }
                }
            } catch (error: Exception) {
                failure = error.message ?: error.javaClass.simpleName
            }
            val text = imported
            val errorMessage = failure
            host.runOnUiThread {
                val chapter = host.activeChapterObject()
                if (host.page != "reader" || targetBookId != host.activeBookId || chapter == null ||
                    targetChapterId != chapter.optString("id")) return@runOnUiThread
                if (errorMessage != null || text == null) {
                    host.toast("وارد کردن متن ناموفق بود: " + errorMessage)
                    return@runOnUiThread
                }
                host.applyChapterText(text, text.length)
                host.chapterEnd = true
                host.putJson(chapter, "end", true)
                host.calculateAndStoreChapterEstimate(chapter, text)
                host.updateProgress()
                if (host.saveCurrentChapter()) host.toast("متن Chapter وارد و پایان نوشتن فعال شد.")
            }
        }
    }

    fun runColloquializer(restoreOriginal: Boolean) {
        val editor = host.chapterEditor
        if (host.page != "reader" || editor == null || !host.saveCurrentChapter()) return
        val targetBookId = host.activeBookId
        val targetChapterIndex = host.activeChapter
        val currentText = editor.text.toString()
        val wasColloquialized = host.chapterColloquialized
        val originalFile = host.chapterOriginalFileName() ?: return
        val button = host.readerActionButton
        val icon = host.readerActionIcon
        val spinner = host.readerActionSpinner
        val bottomBar = host.readerBottomBar

        editor.isEnabled = false
        bottomBar?.let { bar ->
            for (i in 0 until bar.childCount) bar.getChildAt(i).isEnabled = false
        }
        button?.isEnabled = false
        icon?.visibility = View.GONE
        spinner?.visibility = View.VISIBLE

        host.colloquializerExecutor.execute {
            val result = host.chapterColloquializerProcessor.process(
                originalFile, currentText, restoreOriginal, wasColloquialized)
            val original = result.source
            val transformed = result.output
            val failure = result.error
            host.runOnUiThread {
                spinner?.visibility = View.GONE
                icon?.visibility = View.VISIBLE
                button?.isEnabled = true
                bottomBar?.let { bar ->
                    for (i in 0 until bar.childCount) bar.getChildAt(i).isEnabled = true
                }
                editor.isEnabled = true
                if (host.page != "reader" || host.chapterEditor !== editor ||
                    targetBookId != host.activeBookId || targetChapterIndex != host.activeChapter) return@runOnUiThread
                if (failure != null || original == null || transformed == null) {
                    host.toast("محاوره‌ای‌سازی ناموفق بود: " + failure)
                    return@runOnUiThread
                }
                if (restoreOriginal || transformed == original) {
                    if (wasColloquialized || restoreOriginal) {
                        setColloquializerText(original, false)
                        if (host.saveCurrentChapter()) host.storage.deleteChapterOriginal(originalFile)
                    } else {
                        host.storage.deleteChapterOriginal(originalFile)
                    }
                    if (!restoreOriginal) host.toast("تغییری برای اعمال پیدا نشد.")
                    else host.toast("متن اصلی Chapter برگشت.")
                    return@runOnUiThread
                }
                setColloquializerText(transformed, true)
                if (host.saveCurrentChapter()) host.toast("متن محاوره‌ای Chapter ذخیره شد.")
            }
        }
    }

    fun setColloquializerText(value: String, colloquialized: Boolean) {
        val editor = host.chapterEditor ?: return
        val refreshPreview = !host.readerEditing && host.readerPreview != null
        if (refreshPreview) {
            host.pendingReaderScrollFraction = host.readerScrollFraction()
            host.hasPendingReaderScrollFraction = true
        }
        val current = editor.text.toString()
        host.chapterEditHistory.pushUndo(current)
        val selection = editor.selectionStart.coerceAtLeast(0)
        host.chapterColloquialized = colloquialized
        host.applyingChapterColloquializer = true
        editor.setText(value)
        editor.setSelection(selection.coerceAtMost(value.length))
        host.applyingChapterColloquializer = false
        host.chapterText = value
        host.chapterDirty = true
        host.activeChapterObject()?.let { host.putJson(it, "colloquialized", colloquialized) }
        host.updateProgress()
        if (refreshPreview) {
            host.restoreReaderAfterRender = true
            host.renderReaderPreview()
        }
    }
}
